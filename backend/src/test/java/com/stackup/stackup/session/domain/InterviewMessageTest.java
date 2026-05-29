package com.stackup.stackup.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stackup.stackup.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewMessageTest {

    // ── factory happy-path ─────────────────────────────────────────────────

    @Test
    void interviewer_creates_interviewer_message_with_sequence() {
        InterviewSession s = newSession();
        InterviewMessage m = InterviewMessage.interviewer(s, 1, "왜 PG 를 골랐나요?", null);
        assertThat(m.getRole()).isEqualTo(MessageRole.INTERVIEWER);
        assertThat(m.getSequenceNumber()).isEqualTo(1);
        assertThat(m.getContent()).isEqualTo("왜 PG 를 골랐나요?");
        assertThat(m.getStatus()).isEqualTo(MessageStatus.CREATED);
        assertThat(m.getParentMessage()).isNull();
        assertThat(m.getSession()).isSameAs(s);
    }

    @Test
    void interviewee_creates_interviewee_message_with_parent() {
        InterviewSession s = newSession();
        InterviewMessage parent = InterviewMessage.interviewer(s, 1, "Q", null);
        InterviewMessage answer = InterviewMessage.interviewee(s, 2, "A", parent);
        assertThat(answer.getRole()).isEqualTo(MessageRole.INTERVIEWEE);
        assertThat(answer.getParentMessage()).isSameAs(parent);
        assertThat(answer.getStatus()).isEqualTo(MessageStatus.COMPLETED);
        assertThat(answer.getSequenceNumber()).isEqualTo(2);
        assertThat(answer.getContent()).isEqualTo("A");
    }

    // ── null / blank / range guards ────────────────────────────────────────

    @Test
    void interviewer_throws_when_session_null() {
        assertThatThrownBy(() -> InterviewMessage.interviewer(null, 1, "Q", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("session");
    }

    @Test
    void interviewer_throws_when_content_null() {
        assertThatThrownBy(() -> InterviewMessage.interviewer(newSession(), 1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void interviewer_throws_when_content_blank() {
        assertThatThrownBy(() -> InterviewMessage.interviewer(newSession(), 1, "   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void interviewer_throws_when_sequence_less_than_1() {
        assertThatThrownBy(() -> InterviewMessage.interviewer(newSession(), 0, "Q", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequence");
    }

    @Test
    void interviewee_throws_when_session_null() {
        assertThatThrownBy(() -> InterviewMessage.interviewee(null, 1, "A", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("session");
    }

    @Test
    void interviewee_throws_when_content_blank() {
        assertThatThrownBy(() -> InterviewMessage.interviewee(newSession(), 1, "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void interviewee_throws_when_content_null() {
        InterviewSession s = newSession();
        InterviewMessage parent = InterviewMessage.interviewer(s, 1, "Q", null);
        assertThatThrownBy(() -> InterviewMessage.interviewee(s, 2, null, parent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void interviewee_throws_when_sequence_less_than_1() {
        assertThatThrownBy(() -> InterviewMessage.interviewee(newSession(), -1, "A", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequence");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private InterviewSession newSession() {
        User user = userStub(1L);
        return InterviewSession.create(
                user, "테스트 세션", null,
                SessionMode.ONLINE, InterviewType.TECHNICAL, JobCategory.BACKEND,
                10, 60
        );
    }

    private User userStub(Long id) {
        User user = User.createGithubUser(
                123L, "stub-user", "stub@example.com", null, "encrypted-token"
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
