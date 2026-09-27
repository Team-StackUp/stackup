package com.stackup.stackup.session.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * `callback.questions(FOLLOWUP)` 이 유실되면 꼬리질문이 `(생성 중)` 으로 남고 답할 질문이
 * 없어 면접이 멈춘다. 음성 답변의 `(transcribing)` 과 같은 구멍이다.
 */
@ExtendWith(MockitoExtension.class)
class StaleFollowupSweeperTest {

    @Mock InterviewMessageRepository messageRepository;
    @Mock QuestionsCallbackService callbackService;
    @InjectMocks StaleFollowupSweeper sweeper;

    @Test
    void 멈춘_placeholder_를_복구한다() {
        ReflectionTestUtils.setField(sweeper, "staleAfterMinutes", 3L);
        when(messageRepository.findStaleFollowupPlaceholders(anyString(), any(Instant.class)))
            .thenReturn(List.of(placeholder(444L), placeholder(445L)));

        sweeper.sweep();

        verify(callbackService).failStaleFollowup(444L);
        verify(callbackService).failStaleFollowup(445L);
    }

    // sentinel 문자열이 어긋나면 조회가 조용히 0건이 되고 스위퍼가 아무 일도 안 한다.
    @Test
    void 조회는_생성중_sentinel_과_임계시각으로_한다() {
        ReflectionTestUtils.setField(sweeper, "staleAfterMinutes", 3L);
        when(messageRepository.findStaleFollowupPlaceholders(anyString(), any(Instant.class)))
            .thenReturn(List.of());

        Instant before = Instant.now();
        sweeper.sweep();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(messageRepository).findStaleFollowupPlaceholders(text.capture(), cutoff.capture());
        org.assertj.core.api.Assertions.assertThat(text.getValue())
            .isEqualTo(InterviewMessage.FOLLOWUP_GENERATING_TEXT);
        org.assertj.core.api.Assertions.assertThat(cutoff.getValue())
            .isBefore(before.minusSeconds(170));
        verifyNoInteractions(callbackService);
    }

    // 한 건이 터져도 나머지는 정리돼야 한다 — 멈춘 면접이 남는 게 이 스위퍼가 막으려는 것이다.
    @Test
    void 한_건이_실패해도_나머지를_계속_처리한다() {
        ReflectionTestUtils.setField(sweeper, "staleAfterMinutes", 3L);
        when(messageRepository.findStaleFollowupPlaceholders(anyString(), any(Instant.class)))
            .thenReturn(List.of(placeholder(444L), placeholder(445L)));
        doThrow(new IllegalStateException("boom")).when(callbackService).failStaleFollowup(444L);

        sweeper.sweep();

        verify(callbackService).failStaleFollowup(445L);
    }

    @Test
    void 대상이_없으면_아무것도_하지_않는다() {
        ReflectionTestUtils.setField(sweeper, "staleAfterMinutes", 3L);
        when(messageRepository.findStaleFollowupPlaceholders(anyString(), any(Instant.class)))
            .thenReturn(List.of());

        sweeper.sweep();

        verify(callbackService, never()).failStaleFollowup(any());
    }

    private InterviewMessage placeholder(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession session = InterviewSession.create(
            user, "면접", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND), 5, 30, null, null);
        ReflectionTestUtils.setField(session, "id", 10L);
        session.start();

        InterviewMessage parent = InterviewMessage.interviewer(session, 1, "질문");
        InterviewMessage m = InterviewMessage.followupPlaceholder(session, 3, parent);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}
