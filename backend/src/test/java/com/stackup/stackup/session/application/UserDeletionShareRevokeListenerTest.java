package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.application.event.UserDeletedEvent;
import com.stackup.stackup.user.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserDeletionShareRevokeListenerTest {

    @Mock private SessionFeedbackRepository feedbackRepository;

    @InjectMocks private UserDeletionShareRevokeListener listener;

    @Test
    void userDeleted_disablesAllSharedFeedbackOfThatUser() {
        SessionFeedback shared = feedbackFixture();
        shared.enableShare("token-123");
        when(feedbackRepository.findSharedByOwner(7L)).thenReturn(List.of(shared));

        listener.on(new UserDeletedEvent(7L));

        // 회원 탈퇴 후에도 PublicFeedbackController.getByToken 은 User.deleted 를 보지 않고
        // feedback.shareToken 만 본다 — 여기서 끊지 않으면 탈퇴한 사용자의 피드백을
        // 누구나 링크로 계속 볼 수 있다.
        assertThat(shared.getShareToken()).isNull();
    }

    private SessionFeedback feedbackFixture() {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 7L);
        InterviewSession session = InterviewSession.create(user, "t", null, SessionMode.TECHNICAL,
            JobCategory.BACKEND, 5, 30, null, null);
        ReflectionTestUtils.setField(session, "id", 99L);
        return SessionFeedback.of(session, 80.0, 80.0, 80.0, 80.0,
            "강점", "약점", null, null, null, null, null);
    }
}
