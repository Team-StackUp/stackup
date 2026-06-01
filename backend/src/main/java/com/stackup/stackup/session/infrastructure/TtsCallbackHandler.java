package com.stackup.stackup.session.infrastructure;

import com.stackup.stackup.session.application.TtsCallbackService;
import com.stackup.stackup.session.application.dto.TtsCallbackEnvelope;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TtsCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(TtsCallbackHandler.class);

    private final TtsCallbackService callbackService;

    @RabbitListener(queues = "${app.messaging.rabbitmq.queues.names.core-callback-tts}")
    public void handle(TtsCallbackEnvelope envelope) {
        try {
            callbackService.apply(envelope);
        } catch (RuntimeException e) {
            log.error("callback.tts processing failed. messageId={}",
                envelope == null ? null : envelope.messageId(), e);
            throw e;
        }
    }
}
