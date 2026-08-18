package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.session.application.VoiceAnswerUploadService.VoicePlaceholder;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.VoiceAnswerUploadCommand;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoiceAnswerSubmitServiceTest {

    private static final String KEY = "interview/voice/raw/10/200.webm";

    @Mock VoiceAnswerUploadService uploadService;
    @Mock ObjectStorageClient storage;
    @InjectMocks VoiceAnswerSubmitService service;

    // S3 PUT 은 반드시 placeholder 생성 이후 · 오디오 부착 이전에, 트랜잭션 밖에서 일어난다.
    @Test
    void submit_putsAudioBetweenPlaceholderAndAttach() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "Tell me about ACID.");
        ReflectionTestUtils.setField(question, "id", 100L);
        InterviewMessage placeholder = InterviewMessage.voiceInterviewee(session, 2, question, "idem-1");
        ReflectionTestUtils.setField(placeholder, "id", 200L);
        MessageResult attached = MessageResult.of(placeholder);

        when(uploadService.createVoicePlaceholder(1L, 10L, "idem-1"))
            .thenReturn(new VoicePlaceholder(session, placeholder, question));
        when(uploadService.attachAudioAndRequestAnalysis(1L, 10L, 200L, KEY, "audio/webm"))
            .thenReturn(attached);

        MessageResult result = service.submit(1L, 10L,
            command("voice-bytes".getBytes(), "audio/webm", "idem-1"));

        assertThat(result).isSameAs(attached);
        verify(storage).put(eq(KEY), any(), eq(11L), eq("audio/webm"));

        InOrder order = inOrder(uploadService, storage);
        order.verify(uploadService).createVoicePlaceholder(1L, 10L, "idem-1");
        order.verify(storage).put(eq(KEY), any(), anyLong(), any());
        order.verify(uploadService).attachAudioAndRequestAnalysis(1L, 10L, 200L, KEY, "audio/webm");
    }

    // 코덱 파라미터가 붙은 MediaRecorder MIME 도 확장자 매핑이 되어야 한다.
    @Test
    void submit_stripsCodecParameterWhenBuildingKey() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "q");
        InterviewMessage placeholder = InterviewMessage.voiceInterviewee(session, 2, question, null);
        ReflectionTestUtils.setField(placeholder, "id", 200L);

        when(uploadService.createVoicePlaceholder(1L, 10L, null))
            .thenReturn(new VoicePlaceholder(session, placeholder, question));
        when(uploadService.attachAudioAndRequestAnalysis(
            1L, 10L, 200L, KEY, "audio/webm;codecs=opus")).thenReturn(MessageResult.of(placeholder));

        service.submit(1L, 10L, command("x".getBytes(), "audio/webm;codecs=opus", null));

        verify(storage).put(eq(KEY), any(), anyLong(), eq("audio/webm;codecs=opus"));
    }

    // 같은 Idempotency-Key 재요청: 이미 업로드된 메시지는 재업로드·재발행하지 않는다.
    @Test
    void submit_returnsExisting_whenAudioAlreadyAttached() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "q");
        InterviewMessage placeholder = InterviewMessage.voiceInterviewee(session, 2, question, "idem-1");
        ReflectionTestUtils.setField(placeholder, "id", 200L);
        placeholder.attachAudio(KEY);
        MessageResult described = MessageResult.of(placeholder);

        when(uploadService.createVoicePlaceholder(1L, 10L, "idem-1"))
            .thenReturn(new VoicePlaceholder(session, placeholder, question));
        when(uploadService.describe(200L)).thenReturn(described);

        MessageResult result = service.submit(1L, 10L,
            command("voice".getBytes(), "audio/webm", "idem-1"));

        assertThat(result).isSameAs(described);
        verify(storage, never()).put(any(), any(), anyLong(), any());
        verify(uploadService, never()).attachAudioAndRequestAnalysis(any(), any(), any(), any(), any());
    }

    // S3 업로드가 터지면 placeholder 는 이미 commit 돼 있다. FAILED 로 확정해 턴 잠김을 막는다.
    @Test
    void submit_marksMessageFailed_whenStoragePutThrows() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "q");
        InterviewMessage placeholder = InterviewMessage.voiceInterviewee(session, 2, question, null);
        ReflectionTestUtils.setField(placeholder, "id", 200L);

        when(uploadService.createVoicePlaceholder(1L, 10L, null))
            .thenReturn(new VoicePlaceholder(session, placeholder, question));
        doThrow(new RuntimeException("s3 down"))
            .when(storage).put(any(), any(), anyLong(), any());

        assertThatThrownBy(() -> service.submit(1L, 10L, command("v".getBytes(), "audio/webm", null)))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.VOICE_UPLOAD_FAILED));

        verify(uploadService).failVoiceUpload(10L, 200L);
        verify(uploadService, never()).attachAudioAndRequestAnalysis(any(), any(), any(), any(), any());
    }

    @Test
    void submit_rejectsEmptyAudio() {
        assertThatThrownBy(() -> service.submit(1L, 10L, command(new byte[0], "audio/webm", null)))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.VOICE_EMPTY_FILE));

        verifyNoInteractions(uploadService, storage);
    }

    @Test
    void submit_rejectsTooLargeAudio() {
        VoiceAnswerUploadCommand cmd = new VoiceAnswerUploadCommand(
            new ByteArrayInputStream(new byte[] {1}),
            25L * 1024 * 1024 + 1,
            "audio/webm",
            "a.webm",
            null
        );

        assertThatThrownBy(() -> service.submit(1L, 10L, cmd))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.VOICE_FILE_TOO_LARGE));

        verifyNoInteractions(uploadService, storage);
    }

    @Test
    void submit_rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> service.submit(1L, 10L, command("x".getBytes(), "text/plain", null)))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.VOICE_INVALID_CONTENT_TYPE));

        verifyNoInteractions(uploadService, storage);
    }

    private VoiceAnswerUploadCommand command(byte[] content, String contentType, String idempotencyKey) {
        return new VoiceAnswerUploadCommand(
            new ByteArrayInputStream(content),
            content.length,
            contentType,
            "answer.webm",
            idempotencyKey
        );
    }

    private InterviewSession sessionInProgress(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null
        );
        ReflectionTestUtils.setField(s, "id", id);
        s.start();
        return s;
    }
}
