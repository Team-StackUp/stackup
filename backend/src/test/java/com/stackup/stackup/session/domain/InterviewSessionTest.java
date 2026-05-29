package com.stackup.stackup.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stackup.stackup.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewSessionTest {

    @Test
    void create_initializes_ready_with_explicit_values() {
        User user = userStub(1L);
        InterviewSession s = InterviewSession.create(
                user, "Title", null, SessionMode.ONLINE, InterviewType.TECHNICAL,
                JobCategory.BACKEND, 10, 60
        );
        assertThat(s.getStatus()).isEqualTo(SessionStatus.READY);
        assertThat(s.getTotalQuestionCount()).isZero();
        assertThat(s.getStartedAt()).isNull();
    }

    @Test
    void create_applies_null_defaults_for_max_questions_and_max_duration() {
        User user = userStub(1L);
        InterviewSession s = InterviewSession.create(
                user, "Title", null, SessionMode.ONLINE, InterviewType.TECHNICAL,
                JobCategory.BACKEND, null, null
        );
        assertThat(s.getMaxQuestions()).isEqualTo(10);
        assertThat(s.getMaxDurationMinutes()).isEqualTo(60);
    }

    @Test
    void create_throws_when_user_null() {
        assertThatThrownBy(() -> InterviewSession.create(
                null, null, null, SessionMode.ONLINE, InterviewType.TECHNICAL,
                JobCategory.BACKEND, 10, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_throws_when_mode_null() {
        assertThatThrownBy(() -> InterviewSession.create(
                userStub(1L), null, null, null, InterviewType.TECHNICAL,
                JobCategory.BACKEND, 10, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_throws_when_interviewType_null() {
        assertThatThrownBy(() -> InterviewSession.create(
                userStub(1L), null, null, SessionMode.ONLINE, null,
                JobCategory.BACKEND, 10, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_throws_when_jobCategory_null() {
        assertThatThrownBy(() -> InterviewSession.create(
                userStub(1L), null, null, SessionMode.ONLINE, InterviewType.TECHNICAL,
                null, 10, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markInProgress_transitions_to_in_progress_and_sets_startedAt() {
        InterviewSession s = newReady();
        s.markInProgress();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(s.getStartedAt()).isNotNull();
    }

    @Test
    void markInProgress_fails_when_not_ready() {
        InterviewSession s = newReady();
        s.markInProgress();
        assertThatThrownBy(s::markInProgress)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void incrementQuestionCount_advances_counter() {
        InterviewSession s = newInProgress();
        s.incrementQuestionCount();
        assertThat(s.getTotalQuestionCount()).isEqualTo(1);
    }

    @Test
    void end_completed_when_in_progress() {
        InterviewSession s = newInProgress();
        s.end();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(s.getEndedAt()).isNotNull();
    }

    @Test
    void end_fails_when_not_in_progress() {
        InterviewSession s = newReady();
        assertThatThrownBy(s::end).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isMaxReached_returns_true_when_question_count_equals_max() {
        InterviewSession s = newInProgress();
        for (int i = 0; i < 10; i++) s.incrementQuestionCount();
        assertThat(s.isMaxReached()).isTrue();
    }

    @Test
    void cancel_transitions_from_ready() {
        InterviewSession s = newReady();
        s.cancel();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(s.getEndedAt()).isNotNull();
    }

    @Test
    void cancel_fails_when_already_completed() {
        InterviewSession s = newInProgress();
        s.end();
        assertThatThrownBy(s::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancel_fails_when_already_cancelled() {
        InterviewSession s = newReady();
        s.cancel();
        assertThatThrownBy(s::cancel).isInstanceOf(IllegalStateException.class);
    }

    // helpers
    private InterviewSession newReady() {
        return InterviewSession.create(userStub(1L), null, null,
                SessionMode.ONLINE, InterviewType.TECHNICAL, JobCategory.BACKEND, 10, 60);
    }

    private InterviewSession newInProgress() {
        InterviewSession s = newReady();
        s.markInProgress();
        return s;
    }

    private User userStub(Long id) {
        User user = User.createGithubUser(
                123L, "stub-user", "stub@example.com", null, "encrypted-token"
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
