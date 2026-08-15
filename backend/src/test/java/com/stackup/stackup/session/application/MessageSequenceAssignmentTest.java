package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 메시지 시퀀스는 개수가 아니라 최대값 기준으로 매겨야 한다.
 *
 * <p>(session_id, sequence_number) 에 유니크 제약이 있고 placeholder 를 삭제하는 경로(DONT_KNOW)가
 * 있어서, 개수 기준으로 매기면 삭제된 메시지가 마지막이 아니게 되는 순간 이미 쓰인 번호를 다시
 * 발급해 INSERT 가 터진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageSequenceAssignmentTest {

    @Mock private RabbitMessagePublisher publisher;
    @Mock private RabbitMqProperties properties;
    @Mock private InterviewMessageRepository messageRepository;
    @Mock private SessionContextRepository contextRepository;
    @Mock private QuestionsCallbackService questionsCallbackService;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private SessionFollowupRequester requester;

    @Test
    void followupPlaceholder_usesMaxSequenceNotCount() {
        InterviewSession session = sessionFixture();
        InterviewMessage question = InterviewMessage.interviewer(session, 5, "질문?");
        ReflectionTestUtils.setField(question, "id", 500L);
        InterviewMessage answer = InterviewMessage.interviewee(session, 6, "답변", question, null);
        ReflectionTestUtils.setField(answer, "id", 501L);

        when(messageRepository.findById(500L)).thenReturn(Optional.of(question));
        when(messageRepository.findById(501L)).thenReturn(Optional.of(answer));
        when(contextRepository.findBySession_Id(99L)).thenReturn(List.of());
        when(messageRepository.findBySession_IdOrderBySequenceNumberAsc(99L))
            .thenReturn(List.of(question, answer));
        // 삭제된 메시지가 있어 개수(4)와 최대 시퀀스(6)가 어긋난 상태.
        when(messageRepository.countBySession_Id(99L)).thenReturn(4L);
        when(messageRepository.findMaxSequenceBySessionId(99L)).thenReturn(6);
        when(properties.routingKeys()).thenReturn(routingKeys());
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(i -> i.getArgument(0));

        requester.onAnswerSubmitted(new AnswerSubmittedEvent(7L, 99L, 500L, 501L));

        ArgumentCaptor<InterviewMessage> saved = ArgumentCaptor.forClass(InterviewMessage.class);
        org.mockito.Mockito.verify(messageRepository).save(saved.capture());
        // 개수 기준이면 5 — 이미 쓰인 번호라 유니크 제약에 걸린다.
        assertThat(saved.getValue().getSequenceNumber()).isEqualTo(7);
    }

    private InterviewSession sessionFixture() {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 7L);
        InterviewSession session = InterviewSession.create(user, "t", null, SessionMode.TECHNICAL,
            JobCategory.BACKEND, 5, 30, null, null);
        ReflectionTestUtils.setField(session, "id", 99L);
        session.start();
        return session;
    }

    private RabbitMqProperties.RoutingKeyProperties routingKeys() {
        return new RabbitMqProperties.RoutingKeyProperties(
            "x", "x", "x", "x", "generate.followup", "x", "x", "x",
            "x", "x", "x", "x", "x", "x", "x", "x"
        );
    }
}
