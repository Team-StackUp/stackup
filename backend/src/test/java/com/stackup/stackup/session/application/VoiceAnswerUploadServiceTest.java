package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.session.application.dto.AnalyzeVoicePayload;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.VoiceAnswerUploadCommand;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoiceAnswerUploadServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock ObjectStorageClient storage;
    @Mock RabbitMessagePublisher publisher;

    VoiceAnswerUploadService service;

    @BeforeEach
    void setUp() {
        service = new VoiceAnswerUploadService(
            sessionRepository,
            messageRepository,
            storage,
            publisher,
            rabbitMqProperties()
        );
    }

    @Test
    void submit_savesAudioAndPublishesAnalyzeVoice() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "Tell me about ACID.");
        ReflectionTestUtils.setField(question, "id", 100L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L)).thenReturn(Optional.of(question));
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 200L);
            return m;
        });

        MessageResult result = service.submit(1L, 10L,
            command("voice-bytes".getBytes(), "audio/webm", "idem-1"));

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.status()).isEqualTo(MessageStatus.CREATED);
        assertThat(result.audioFilePath()).isEqualTo("interview/voice/raw/10/200.webm");
        verify(storage).put(eq("interview/voice/raw/10/200.webm"), any(), eq(11L), eq("audio/webm"));

        ArgumentCaptor<AnalyzeVoicePayload> payloadCaptor = ArgumentCaptor.forClass(AnalyzeVoicePayload.class);
        verify(publisher).publishToAi(eq("analyze.voice"), payloadCaptor.capture(), any(MessageContext.class));
        AnalyzeVoicePayload payload = payloadCaptor.getValue();
        assertThat(payload.sessionId()).isEqualTo(10L);
        assertThat(payload.messageId()).isEqualTo(200L);
        assertThat(payload.parentQuestionMessageId()).isEqualTo(100L);
        assertThat(payload.audioS3Key()).isEqualTo("interview/voice/raw/10/200.webm");
        assertThat(payload.previousQuestionText()).isEqualTo("Tell me about ACID.");
    }

    @Test
    void submit_rejectsEmptyAudio() {
        assertThatThrownBy(() -> service.submit(1L, 10L,
            command(new byte[0], "audio/webm", null)))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.VOICE_EMPTY_FILE));

        verify(sessionRepository, never()).findByIdAndUser_IdAndDeletedFalse(any(), any());
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
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.VOICE_FILE_TOO_LARGE));
    }

    @Test
    void submit_rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> service.submit(1L, 10L,
            command("x".getBytes(), "text/plain", null)))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.VOICE_INVALID_CONTENT_TYPE));
    }

    @Test
    void submit_rejectsWhenSessionIsNotInProgress() {
        InterviewSession session = sessionFixture(10L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.submit(1L, 10L,
            command("voice".getBytes(), "audio/webm", null)))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_INVALID_STATE));

        verify(storage, never()).put(any(), any(), anyLong(), any());
        verify(publisher, never()).publishToAi(any(), any(), any());
    }

    @Test
    void submit_rejectsWhenNoQuestionMessageExists() {
        InterviewSession session = sessionInProgress(10L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(1L, 10L,
            command("voice".getBytes(), "audio/webm", null)))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_INVALID_STATE));

        verify(storage, never()).put(any(), any(), anyLong(), any());
        verify(publisher, never()).publishToAi(any(), any(), any());
    }

    @Test
    void submit_rejectsWhenLastMessageIsNotQuestion() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage priorAnswer = InterviewMessage.interviewee(session, 1, "already answered", null, null);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L)).thenReturn(Optional.of(priorAnswer));

        assertThatThrownBy(() -> service.submit(1L, 10L,
            command("voice".getBytes(), "audio/webm", null)))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_INVALID_STATE));

        verify(storage, never()).put(any(), any(), anyLong(), any());
        verify(publisher, never()).publishToAi(any(), any(), any());
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
        InterviewSession s = sessionFixture(id);
        s.start();
        return s;
    }

    private InterviewSession sessionFixture(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30
        );
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private RabbitMqProperties rabbitMqProperties() {
        return new RabbitMqProperties(
            "core",
            "1",
            new RabbitMqProperties.Message("application/json", "UTF-8", "X-Trace-Id"),
            new RabbitMqProperties.Template(true),
            new RabbitMqProperties.Exchanges(true, false,
                new RabbitMqProperties.Exchanges.Names("core.ai", "ai.core", "realtime")),
            new RabbitMqProperties.Queues(true,
                new RabbitMqProperties.Queues.Names(
                    "ai.analyze.resume",
                    "ai.analyze.repository",
                    "ai.generate.questions",
                    "ai.generate.followup",
                    "ai.generate.feedback",
                    "ai.analyze.voice",
                    "ai.generate.tts",
                    "core.callback.analysis",
                    "core.callback.questions",
                    "core.callback.feedback",
                    "core.callback.voice",
                    "core.callback.tts"
                )),
            new RabbitMqProperties.RoutingKeyProperties(
                "analyze.resume",
                "analyze.repository",
                "generate.questions",
                "generate.followup",
                "generate.feedback",
                "analyze.voice",
                "generate.tts",
                "callback.analysis",
                "callback.questions",
                "callback.feedback",
                "callback.voice",
                "callback.tts",
                "session.notify",
                "realtime.user.notify",
                "realtime.document.notify"
            ),
            new RabbitMqProperties.DeadLetter("dlx", "dlq."),
            new RabbitMqProperties.Retry(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10))
        );
    }
}
