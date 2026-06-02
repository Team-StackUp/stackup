package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.dto.GenerateTtsPayload;
import com.stackup.stackup.session.application.event.QuestionPersistedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 질문(INTERVIEWER) 메시지 commit 후 발화 → generate.tts envelope 발행 (Part A).
@Component
@RequiredArgsConstructor
public class SessionTtsRequester {

    private static final Logger log = LoggerFactory.getLogger(SessionTtsRequester.class);

    private final RabbitMessagePublisher publisher;
    private final RabbitMqProperties properties;
    private final InterviewMessageRepository messageRepository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuestionPersisted(QuestionPersistedEvent event) {
        InterviewMessage message = messageRepository.findById(event.messageId()).orElse(null);
        if (message == null) {
            log.warn("generate.tts skipped — message not found. messageId={}", event.messageId());
            return;
        }
        InterviewSession session = message.getSession();
        GenerateTtsPayload payload = new GenerateTtsPayload(
            session.getId(),
            message.getId(),
            message.getContent(),
            session.getMode(),
            session.getJobCategory()
        );
        publisher.publishToAi(
            properties.routingKeys().generateTts(),
            payload,
            new MessageContext(event.userId(), session.getId(), null, null)
        );
        log.info("generate.tts published. sessionId={}, messageId={}", session.getId(), message.getId());
    }
}
