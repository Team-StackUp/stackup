package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.application.event.SelfIntroAnsweredEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionFollowupRequesterTest {

    @Mock RabbitMessagePublisher publisher;
    @Mock RabbitMqProperties properties;
    @Mock InterviewMessageRepository messageRepository;
    @Mock SessionContextRepository contextRepository;
    @Mock QuestionsCallbackService questionsCallbackService;
    @Mock ApplicationEventPublisher events;
    @InjectMocks SessionFollowupRequester requester;

    // 자기소개(첫 질문) 답변이면 꼬리질문을 만들지 않고 SelfIntroAnsweredEvent 만 발행한다.
    @Test
    void onAnswerSubmitted_selfIntroAnswer_triggersPoolInsteadOfFollowup() {
        InterviewSession session = sessionFixture();
        InterviewMessage selfIntro = InterviewMessage.selfIntroduction(session, 1);
        ReflectionTestUtils.setField(selfIntro, "id", 100L);
        InterviewMessage answer = InterviewMessage.interviewee(session, 2, "제 소개를 드리면…", selfIntro, null);
        ReflectionTestUtils.setField(answer, "id", 101L);

        when(messageRepository.findById(100L)).thenReturn(Optional.of(selfIntro));
        when(messageRepository.findById(101L)).thenReturn(Optional.of(answer));
        when(contextRepository.findBySession_Id(11L)).thenReturn(List.of());

        requester.onAnswerSubmitted(new AnswerSubmittedEvent(1L, 11L, 100L, 101L));

        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue()).isInstanceOf(SelfIntroAnsweredEvent.class);
        SelfIntroAnsweredEvent published = (SelfIntroAnsweredEvent) ev.getValue();
        assertThat(published.sessionId()).isEqualTo(11L);
        assertThat(published.selfIntroAnswer()).isEqualTo("제 소개를 드리면…");
        assertThat(published.jobCategories()).containsExactly(JobCategory.BACKEND);

        // 꼬리질문 placeholder INSERT 도, generate.followup 발행도 일어나지 않는다.
        verify(messageRepository, never()).save(any(InterviewMessage.class));
        verify(publisher, never()).publishToAi(any(), any(), any(MessageContext.class));
    }

    private InterviewSession sessionFixture() {
        InterviewSession s = InterviewSession.create(
            com.stackup.stackup.user.domain.User.createGithubUser(1L, "u", null, null, "t"),
            "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, 3, 2);
        ReflectionTestUtils.setField(s, "id", 11L);
        return s;
    }
}
