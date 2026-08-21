package com.stackup.stackup.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.support.PostgresRepositoryTest;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * 통계 쿼리는 삭제된 세션을 빼야 한다.
 *
 * <p>UserStatsService 의 총/완료 카운트는 `countByUser_IdAndDeletedFalse` 로 이미 빼고
 * 있어서, 여기서 안 빼면 같은 화면 안에서 "완료 1회"인데 추이엔 점이 2개 찍힌다.
 * 무엇보다 사용자가 '기록 삭제'로 기대하는 것은 통계에서도 사라지는 것이다.
 */
@PostgresRepositoryTest
class SessionFeedbackRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired InterviewSessionRepository sessionRepository;
    @Autowired SessionFeedbackRepository feedbackRepository;

    @Test
    void statsQueriesExcludeDeletedSessions() {
        User user = userRepository.save(User.createGithubUser(99001L, "stats-user", null, null, "t"));

        InterviewSession kept = sessionRepository.save(startedSession(user));
        InterviewSession removed = sessionRepository.save(startedSession(user));
        feedbackRepository.save(feedback(kept, 80.0));
        feedbackRepository.save(feedback(removed, 40.0));

        // 지우기 전에는 둘 다 잡힌다 — 필터가 "아무것도 안 거르는" 상태와 구분되게.
        assertThat(feedbackRepository.findRecentByOwner(user.getId(), PageRequest.of(0, 10))).hasSize(2);
        assertThat(feedbackRepository.averageOverallScore(user.getId())).isEqualTo(60.0);

        removed.markDeleted();
        sessionRepository.save(removed);

        assertThat(feedbackRepository.findRecentByOwner(user.getId(), PageRequest.of(0, 10)))
            .hasSize(1);
        assertThat(feedbackRepository.averageOverallScore(user.getId())).isEqualTo(80.0);
        assertThat(feedbackRepository.averageTechnicalAccuracy(user.getId())).isEqualTo(80.0);
        assertThat(feedbackRepository.averageLogicScore(user.getId())).isEqualTo(80.0);
        assertThat(feedbackRepository.averageCommunicationScore(user.getId())).isEqualTo(80.0);
    }

    // 회원 탈퇴 시 공유 토큰을 회수하는 경로는 반대다 — 삭제된 세션의 토큰도 거둬야
    // 살아있는 공유 링크가 남지 않는다. 통계와 같이 필터를 걸면 안 되는 이유.
    @Test
    void findSharedByOwnerIncludesDeletedSessions() {
        User user = userRepository.save(User.createGithubUser(99002L, "share-user", null, null, "t"));
        InterviewSession removed = sessionRepository.save(startedSession(user));
        SessionFeedback shared = feedbackRepository.save(feedback(removed, 70.0));
        shared.enableShare("token-for-deleted-session");
        feedbackRepository.save(shared);

        removed.markDeleted();
        sessionRepository.save(removed);

        assertThat(feedbackRepository.findSharedByOwner(user.getId()))
            .extracting(SessionFeedback::getShareToken)
            .contains("token-for-deleted-session");
    }

    private InterviewSession startedSession(User user) {
        InterviewSession s = InterviewSession.create(
            user, "통계 검증", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND), 5, 30, null, null);
        s.start();
        return s;
    }

    private SessionFeedback feedback(InterviewSession session, Double score) {
        return SessionFeedback.of(session, score, score, score, score,
            "강점", "약점", "[]", "[]", "[]", "[]", null);
    }
}
