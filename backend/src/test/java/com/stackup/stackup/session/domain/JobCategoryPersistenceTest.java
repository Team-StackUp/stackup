package com.stackup.stackup.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.support.PostgresRepositoryTest;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 새 직군이 <b>실제로 저장되는지</b> 확인한다.
 *
 * <p>enum 에 값을 추가해도 DB CHECK 제약이 그대로면 그 직군으로 만든 세션은 INSERT 에서
 * 터진다. 그리고 그 실패는 <b>사용자가 면접을 시작하는 순간</b>에만 드러난다 — enum·프롬프트·
 * UI 를 다 고쳐 놓고 마이그레이션 한 줄을 빠뜨리면 정확히 그렇게 된다.
 *
 * <p>CHECK 제약이 두 곳(interview_sessions, session_job_categories)에 있어서 한 곳만 고쳐도
 * 절반은 통과한다 — 다중 선택까지 저장해 둘 다 건드린다.
 */
@PostgresRepositoryTest
class JobCategoryPersistenceTest {

    @Autowired UserRepository userRepository;
    @Autowired InterviewSessionRepository sessionRepository;

    @Test
    void 비개발_직군으로_세션을_만들_수_있다() {
        User user = userRepository.save(User.createGithubUser(97001L, "non-eng", null, null, "t"));

        InterviewSession saved = sessionRepository.save(InterviewSession.create(
            user, "영업 면접", null, SessionMode.PERSONALITY,
            List.of(JobCategory.SALES), 5, 30, null, null));
        sessionRepository.flush();

        assertThat(saved.getJobCategory()).isEqualTo(JobCategory.SALES);
        assertThat(saved.getJobCategories()).containsExactly(JobCategory.SALES);
    }

    @Test
    void 모든_직군이_CHECK_제약을_통과한다() {
        // 하나라도 마이그레이션 목록에서 빠지면 여기서 터진다.
        long id = 97100L;
        for (JobCategory category : JobCategory.values()) {
            User user = userRepository.save(
                User.createGithubUser(id++, "u" + category.name(), null, null, "t"));
            sessionRepository.save(InterviewSession.create(
                user, category.koreanLabel(), null, SessionMode.TECHNICAL,
                List.of(category), 5, 30, null, null));
        }
        sessionRepository.flush();

        assertThat(sessionRepository.count()).isEqualTo(JobCategory.values().length);
    }

    @Test
    void 개발_비개발_다중_선택도_저장된다() {
        User user = userRepository.save(User.createGithubUser(97200L, "mixed", null, null, "t"));

        InterviewSession saved = sessionRepository.save(InterviewSession.create(
            user, "혼합", null, SessionMode.INTEGRATED,
            List.of(JobCategory.BACKEND, JobCategory.PLANNING), 5, 30, null, null));
        sessionRepository.flush();

        // session_job_categories 쪽 CHECK 까지 건드린다.
        assertThat(saved.getJobCategories())
            .containsExactly(JobCategory.BACKEND, JobCategory.PLANNING);
    }
}
