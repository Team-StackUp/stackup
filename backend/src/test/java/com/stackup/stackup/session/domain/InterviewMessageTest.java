package com.stackup.stackup.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewMessageTest {

    private InterviewSession session;

    @BeforeEach
    void setUp() {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        session = InterviewSession.create(
            user, "테스트 세션", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null
        );
        ReflectionTestUtils.setField(session, "id", 10L);
    }

    @Test
    void followupPlaceholder_hasSentinelContent_createdStatus_interviewerRole_ttsNotRequested() {
        InterviewMessage parent = InterviewMessage.interviewer(session, 1, "원질문");

        InterviewMessage ph = InterviewMessage.followupPlaceholder(session, 2, parent);

        assertThat(ph.getContent()).isEqualTo(InterviewMessage.FOLLOWUP_GENERATING_TEXT);
        assertThat(ph.getStatus()).isEqualTo(MessageStatus.CREATED);
        assertThat(ph.getTtsStatus()).isEqualTo(TtsStatus.NOT_REQUESTED);
        assertThat(ph.getRole()).isEqualTo(MessageRole.INTERVIEWER);
    }

    @Test
    void completeFollowup_fillsContentMarksCompletedAndTtsPending() {
        InterviewMessage parent = InterviewMessage.interviewer(session, 1, "원질문");
        InterviewMessage ph = InterviewMessage.followupPlaceholder(session, 2, parent);

        ph.completeFollowup("진짜 꼬리질문?", false);

        assertThat(ph.getContent()).isEqualTo("진짜 꼬리질문?");
        assertThat(ph.isClarification()).isFalse();
        assertThat(ph.getStatus()).isEqualTo(MessageStatus.COMPLETED);
        assertThat(ph.getTtsStatus()).isEqualTo(TtsStatus.PENDING);
    }

    @Test
    void completeFollowup_withClarificationTrue_setsClarificationFlag() {
        InterviewMessage parent = InterviewMessage.interviewer(session, 1, "원질문");
        InterviewMessage ph2 = InterviewMessage.followupPlaceholder(session, 3, parent);

        ph2.completeFollowup("다시 설명하면…", true);

        assertThat(ph2.isClarification()).isTrue();
        assertThat(ph2.getStatus()).isEqualTo(MessageStatus.COMPLETED);
        assertThat(ph2.getTtsStatus()).isEqualTo(TtsStatus.PENDING);
    }
}
