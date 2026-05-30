package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.dto.GenerateFollowupPayload;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
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

// 답변 commit 후 발화 → generate.followup envelope 발행 (US-19).
@Component
@RequiredArgsConstructor
public class SessionFollowupRequester {

    private static final Logger log = LoggerFactory.getLogger(SessionFollowupRequester.class);

    private final RabbitMessagePublisher publisher;
    private final RabbitMqProperties properties;
    private final InterviewMessageRepository messageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnswerSubmitted(AnswerSubmittedEvent event) {
        InterviewMessage parent = messageRepository.findById(event.parentQuestionMessageId()).orElse(null);
        InterviewMessage answer = messageRepository.findById(event.answerMessageId()).orElse(null);
        if (parent == null || answer == null) {
            log.warn("generate.followup skipped — message not found. parent={}, answer={}",
                event.parentQuestionMessageId(), event.answerMessageId());
            return;
        }
        InterviewSession session = parent.getSession();
        GenerateFollowupPayload payload = new GenerateFollowupPayload(
            session.getId(),
            parent.getId(),
            answer.getId(),
            parent.getContent(),
            answer.getContent(),
            session.getInterviewType(),
            session.getJobCategory()
        );
        publisher.publishToAi(
            properties.routingKeys().generateFollowup(),
            payload,
            new MessageContext(event.userId(), session.getId(), null, null)
        );
        log.info("generate.followup published. sessionId={}, parent={}, answer={}",
            session.getId(), parent.getId(), answer.getId());
    }
}
