package com.stackup.stackup.session.infrastructure;

import com.stackup.stackup.session.application.FeedbackCallbackService;
import com.stackup.stackup.session.application.dto.FeedbackCallbackEnvelope;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// stackup.ai-to-core / callback.feedback 큐 consumer (US-24).
@Component
@RequiredArgsConstructor
public class FeedbackCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(FeedbackCallbackHandler.class);

    private final FeedbackCallbackService callbackService;

    @RabbitListener(queues = "${app.messaging.rabbitmq.queues.names.core-callback-feedback}")
    public void handle(FeedbackCallbackEnvelope envelope) {
        try {
            callbackService.apply(envelope);
        } catch (RuntimeException e) {
            log.error("callback.feedback processing failed. messageId={}",
                envelope == null ? null : envelope.messageId(), e);
            throw e;
        }
    }
}
