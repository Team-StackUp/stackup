package com.stackup.stackup.session.infrastructure;

import com.stackup.stackup.session.application.QuestionsCallbackService;
import com.stackup.stackup.session.application.dto.QuestionsCallbackEnvelope;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// stackup.ai-to-core / callback.questions 큐 consumer. 도메인 갱신은 application 서비스에 위임.
@Component
@RequiredArgsConstructor
public class QuestionsCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(QuestionsCallbackHandler.class);

    private final QuestionsCallbackService callbackService;

    @RabbitListener(queues = "${app.messaging.rabbitmq.queues.names.core-callback-questions}")
    public void handle(QuestionsCallbackEnvelope envelope) {
        try {
            callbackService.apply(envelope);
        } catch (RuntimeException e) {
            log.error("callback.questions processing failed. messageId={}",
                envelope == null ? null : envelope.messageId(), e);
            throw e;
        }
    }
}
