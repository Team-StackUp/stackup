package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.dto.GenerateTtsPayload;
import com.stackup.stackup.session.application.event.QuestionPersistedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.MessageStatus;
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
        // 실패로 확정된 질문은 읽어 주지 않는다. FOLLOWUP 생성 실패/유실도 이 이벤트를 타는데
        // (실패 사실을 화면에 보여야 하므로 SSE 는 그대로 나간다), 그대로 두면 Gemini TTS 를
        // 한 번 써서 "질문 생성에 실패했습니다"를 음성으로 만든다 — 실제로 운영에 2건 있다
        // (messageId=434·437, tts_status=SUCCEEDED). 면접관이 오류 문구를 말하는 셈이고,
        // 대화 기록의 재생 버튼도 그걸 들려준다.
        if (message.getStatus() == MessageStatus.FAILED) {
            log.info("generate.tts skipped — message is FAILED. messageId={}", event.messageId());
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
