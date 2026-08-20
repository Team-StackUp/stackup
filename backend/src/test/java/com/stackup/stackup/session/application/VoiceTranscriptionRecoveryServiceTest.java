package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * `callback.voice` 가 유실되면 음성 답변이 `(transcribing)` 으로 남고 답변 차례가 오지 않아
 * 면접이 멈춘다. 이를 FAILED 로 확정해 기존 STT 실패 경로(텍스트 재답변)를 타게 한다.
 */
@ExtendWith(MockitoExtension.class)
class VoiceTranscriptionRecoveryServiceTest {

    @Mock InterviewMessageRepository messageRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks VoiceTranscriptionRecoveryService service;

    @Test
    void failStaleTranscription_marksFailedAndNotifies() {
        InterviewMessage pending = pendingVoiceAnswer(200L);
        when(messageRepository.findById(200L)).thenReturn(Optional.of(pending));

        service.failStaleTranscription(200L);

        assertThat(pending.getStatus()).isEqualTo(MessageStatus.FAILED);
        // 내용이 실패 안내로 바뀌어야 프론트가 '텍스트로 다시 답변' 턴으로 인식한다.
        assertThat(pending.getContent())
            .isNotEqualTo(InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT);
        // 세션 채널 + 유저 채널 양쪽에 알린다(다른 탭에서도 턴이 풀려야 한다).
        verify(events, times(2)).publishEvent(any(RealtimeNotifyEvent.class));
    }

    // 스위퍼가 목록을 만든 뒤 콜백이 도착했을 수 있다 — 완료된 답변을 실패로 되돌리면 안 된다.
    @Test
    void failStaleTranscription_skipsWhenTranscriptArrivedMeanwhile() {
        InterviewMessage completed = pendingVoiceAnswer(200L);
        completed.completeWithTranscript("실제로는 전사가 도착했습니다");
        when(messageRepository.findById(200L)).thenReturn(Optional.of(completed));

        service.failStaleTranscription(200L);

        assertThat(completed.getStatus()).isEqualTo(MessageStatus.COMPLETED);
        assertThat(completed.getContent()).isEqualTo("실제로는 전사가 도착했습니다");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void failStaleTranscription_isNoopWhenMessageMissing() {
        when(messageRepository.findById(200L)).thenReturn(Optional.empty());

        service.failStaleTranscription(200L);

        verify(events, never()).publishEvent(any());
    }

    private InterviewMessage pendingVoiceAnswer(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession session = InterviewSession.create(
            user, "면접", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND), 5, 30, null, null);
        ReflectionTestUtils.setField(session, "id", 10L);
        session.start();

        InterviewMessage question = InterviewMessage.interviewer(session, 1, "질문");
        InterviewMessage pending = InterviewMessage.voiceInterviewee(session, 2, question, null);
        ReflectionTestUtils.setField(pending, "id", id);
        return pending;
    }
}
