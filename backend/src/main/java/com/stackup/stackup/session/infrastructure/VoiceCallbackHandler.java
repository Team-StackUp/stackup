package com.stackup.stackup.session.infrastructure;

import com.stackup.stackup.session.application.VoiceCallbackService;
import com.stackup.stackup.session.application.dto.VoiceCallbackEnvelope;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// stackup.ai-to-core / callback.voice 큐 consumer (음성 답변 STT/분석 결과).
@Component
@RequiredArgsConstructor
public class VoiceCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceCallbackHandler.class);

    private final VoiceCallbackService callbackService;

    @RabbitListener(queues = "${app.messaging.rabbitmq.queues.names.core-callback-voice}")
    public void handle(VoiceCallbackEnvelope envelope) {
        try {
            callbackService.apply(envelope);
        } catch (RuntimeException e) {
            log.error("callback.voice processing failed. messageId={}",
                envelope == null ? null : envelope.messageId(), e);
            throw e;
        }
    }
}
