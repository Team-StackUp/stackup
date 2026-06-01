package com.stackup.stackup.session.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.session.application.InterviewMessageService;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.presentation.dto.InternalAnswerSubmitRequest;
import com.stackup.stackup.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InternalSessionMessageControllerTest {

    @Mock InterviewMessageService messageService;
    @InjectMocks InternalSessionMessageController controller;

    @Test
    void submit_delegatesToServiceWithUserIdFromBody() {
        when(messageService.submitAnswer(7L, 99L, "내 답변", "idem-1"))
            .thenReturn(MessageResult.of(answerFixture()));

        controller.submit(99L, new InternalAnswerSubmitRequest(7L, "내 답변", "idem-1"));

        verify(messageService).submitAnswer(eq(7L), eq(99L), eq("내 답변"), eq("idem-1"));
    }

    private InterviewMessage answerFixture() {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 7L);
        InterviewSession s = InterviewSession.create(user, "t", null, SessionMode.TECHNICAL,
            JobCategory.BACKEND, 5, 30);
        ReflectionTestUtils.setField(s, "id", 99L);
        InterviewMessage q = InterviewMessage.interviewer(s, 1, "Q?");
        ReflectionTestUtils.setField(q, "id", 500L);
        InterviewMessage a = InterviewMessage.interviewee(s, 2, "내 답변", q, "idem-1");
        ReflectionTestUtils.setField(a, "id", 501L);
        return a;
    }
}
