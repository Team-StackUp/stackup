package com.stackup.stackup.session.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.dto.GenerateTtsPayload;
import com.stackup.stackup.session.application.event.QuestionPersistedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionTtsRequesterTest {

    @Mock RabbitMessagePublisher publisher;
    @Mock RabbitMqProperties properties;
    @Mock RabbitMqProperties.RoutingKeyProperties routingKeys;
    @Mock InterviewMessageRepository messageRepository;
    @InjectMocks SessionTtsRequester requester;

    @Test
    void publishesGenerateTts_whenQuestionPersisted() {
        InterviewSession session = org.mockito.Mockito.mock(InterviewSession.class);
        when(session.getId()).thenReturn(7L);
        InterviewMessage message = org.mockito.Mockito.mock(InterviewMessage.class);
        when(message.getId()).thenReturn(42L);
        when(message.getContent()).thenReturn("당신의 프로젝트에서...");
        when(message.getSession()).thenReturn(session);
        when(messageRepository.findById(42L)).thenReturn(Optional.of(message));
        when(properties.routingKeys()).thenReturn(routingKeys);
        when(routingKeys.generateTts()).thenReturn("generate.tts");

        requester.onQuestionPersisted(new QuestionPersistedEvent(1L, 7L, 42L));

        ArgumentCaptor<GenerateTtsPayload> captor = ArgumentCaptor.forClass(GenerateTtsPayload.class);
        verify(publisher).publishToAi(eq("generate.tts"), captor.capture(), any(MessageContext.class));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().messageId()).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().text()).isEqualTo("당신의 프로젝트에서...");
    }

    // 꼬리질문 생성 실패/유실도 QuestionPersistedEvent 를 탄다(실패 사실을 화면에 보여야
    // 하므로). 그대로 두면 Gemini TTS 를 써서 "질문 생성에 실패했습니다"를 음성으로 만들고
    // 면접관이 오류 문구를 읽는다 — 운영에 2건 있었다(messageId=434·437).
    @Test
    void skips_whenMessageIsFailed() {
        InterviewMessage message = org.mockito.Mockito.mock(InterviewMessage.class);
        when(message.getStatus()).thenReturn(com.stackup.stackup.session.domain.MessageStatus.FAILED);
        when(messageRepository.findById(43L)).thenReturn(Optional.of(message));

        requester.onQuestionPersisted(new QuestionPersistedEvent(1L, 7L, 43L));

        verify(publisher, never()).publishToAi(any(), any(), any());
    }

    @Test
    void skips_whenMessageNotFound() {
        when(messageRepository.findById(99L)).thenReturn(Optional.empty());

        requester.onQuestionPersisted(new QuestionPersistedEvent(1L, 7L, 99L));

        verify(publisher, never()).publishToAi(any(), any(), any());
    }
}
