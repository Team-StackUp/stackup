package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.dto.AnalyzeVoicePayload;
import com.stackup.stackup.session.application.event.VoiceAnswerUploadedEvent;
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

// 음성 답변 오디오 부착 commit 후 발화 → analyze.voice envelope 발행 (US-21).
// 텍스트 답변(SessionFollowupRequester) · 질문 TTS(SessionTtsRequester) 와 동일한 AFTER_COMMIT 패턴.
@Component
@RequiredArgsConstructor
public class VoiceAnalysisRequester {

    private static final Logger log = LoggerFactory.getLogger(VoiceAnalysisRequester.class);

    private final RabbitMessagePublisher publisher;
    private final RabbitMqProperties properties;
    private final InterviewMessageRepository messageRepository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoiceAnswerUploaded(VoiceAnswerUploadedEvent event) {
        InterviewMessage message = messageRepository.findById(event.messageId()).orElse(null);
        if (message == null) {
            log.warn("analyze.voice skipped — message not found. messageId={}", event.messageId());
            return;
        }
        InterviewSession session = message.getSession();
        InterviewMessage parent = message.getParentMessage();

        AnalyzeVoicePayload payload = new AnalyzeVoicePayload(
            session.getId(),
            message.getId(),
            parent == null ? null : parent.getId(),
            event.audioS3Key(),
            event.contentType(),
            parent == null ? null : parent.getContent(),
            session.getMode().name(),
            session.getJobCategory().name()
        );
        publisher.publishToAi(
            properties.routingKeys().analyzeVoice(),
            payload,
            new MessageContext(event.userId(), session.getId(), null, null)
        );
        log.info("analyze.voice published. sessionId={}, messageId={}, key={}",
            session.getId(), message.getId(), event.audioS3Key());
    }
}
