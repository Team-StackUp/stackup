package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.session.application.event.FeedbackRegenerateRequestedEvent;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
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

@ExtendWith(MockitoExtension.class)
class SessionFeedbackQueryServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock SessionFeedbackRepository feedbackRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks SessionFeedbackQueryService service;

    @Test
    void disableShare_clearsToken() {
        InterviewSession session = sessionFixture(50L);
        SessionFeedback feedback = feedbackFixture(session);
        feedback.enableShare("tok-1");
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(feedbackRepository.findBySession_Id(50L)).thenReturn(Optional.of(feedback));

        service.disableShare(1L, 50L);

        assertThat(feedback.getShareToken()).isNull();
    }

    @Test
    void getByToken_hidesFeedbackOfDeletedSession() {
        // 세션을 soft delete 해도 share_token 행은 남는다 — 공개 조회가 이를 계속 노출하면
        // '기록 삭제'가 공유 링크에는 적용되지 않는 개인정보 문제가 된다.
        InterviewSession session = sessionFixture(50L);
        session.markDeleted();
        SessionFeedback feedback = feedbackFixture(session);
        feedback.enableShare("tok-1");
        when(feedbackRepository.findByShareToken("tok-1")).thenReturn(Optional.of(feedback));

        assertThatThrownBy(() -> service.getByToken("tok-1"))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.FEEDBACK_NOT_FOUND));
    }

    @Test
    void get_distinguishesFailureFromPending() {
        // 실패 마커(V29)가 있으면 "생성 중"(NOT_READY)이 아니라 실패 — SSE ERROR 를 놓친
        // 새로고침 클라이언트가 폴링 예산을 헛태우지 않고 즉시 복구 UI 로 전환하는 근거.
        InterviewSession session = sessionFixture(50L);
        session.markFeedbackFailed(true);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(feedbackRepository.findBySession_Id(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L, 50L))
            .isInstanceOfSatisfying(DomainException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.FEEDBACK_GENERATION_FAILED);
                assertThat(e.getDetails()).containsEntry("retriable", true);
            });
    }

    @Test
    void get_returnsNotReadyWithoutFailureMarker() {
        InterviewSession session = sessionFixture(50L);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(feedbackRepository.findBySession_Id(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L, 50L))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.FEEDBACK_NOT_READY));
    }

    @Test
    void regenerate_clearsFailureMarker() {
        // 재생성 요청 = 새 시도 시작 — 마커를 지워 다른 탭/폴링이 "생성 중"으로 복귀한다.
        InterviewSession session = sessionFixture(50L);
        session.start();
        session.end();
        session.markFeedbackFailed(true);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(feedbackRepository.existsBySession_Id(50L)).thenReturn(false);

        service.regenerate(1L, 50L);

        assertThat(session.hasFeedbackFailure()).isFalse();
        verify(events).publishEvent(new FeedbackRegenerateRequestedEvent(1L, 50L));
    }

    @Test
    void regenerate_publishesForCompletedSessionWithoutFeedback() {
        InterviewSession session = sessionFixture(50L);
        session.start();
        session.end();
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(feedbackRepository.existsBySession_Id(50L)).thenReturn(false);

        service.regenerate(1L, 50L);

        // 발행은 직접 하지 않고 이벤트만 — 실제 generate.feedback 은 AFTER_COMMIT 리스너가 발행.
        verify(events).publishEvent(new FeedbackRegenerateRequestedEvent(1L, 50L));
    }

    @Test
    void regenerate_conflictsWhenFeedbackAlreadyExists() {
        InterviewSession session = sessionFixture(50L);
        session.start();
        session.end();
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(feedbackRepository.existsBySession_Id(50L)).thenReturn(true);

        assertThatThrownBy(() -> service.regenerate(1L, 50L))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.FEEDBACK_ALREADY_EXISTS));
        verify(events, never()).publishEvent(new FeedbackRegenerateRequestedEvent(1L, 50L));
    }

    @Test
    void regenerate_rejectsNonCompletedSession() {
        // INTERRUPTED/READY 세션은 피드백 대상이 아니다 — 재생성으로도 만들 수 없어야 한다.
        InterviewSession session = sessionFixture(50L);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.regenerate(1L, 50L))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_INVALID_STATE));
    }

    private InterviewSession sessionFixture(Long id) {
        User user = User.createGithubUser(123L, "octocat", null, null, "tok");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND), 5, 30, null, null
        );
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private SessionFeedback feedbackFixture(InterviewSession session) {
        return SessionFeedback.of(session, 80.0, 80.0, 80.0, 80.0,
            "s", "w", null, null, null, null, null);
    }
}
