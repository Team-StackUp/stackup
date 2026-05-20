package com.stackup.stackup.document.infrastructure;

import com.stackup.stackup.document.application.AnalysisCallbackService;
import com.stackup.stackup.document.application.dto.AnalysisCallbackEnvelope;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * stackup.ai-to-core / callback.analysis 큐 consumer.
 * 메시지 라우팅·역직렬화만 담당하고 실제 도메인 갱신은 application 서비스에 위임.
 */
@Component
@RequiredArgsConstructor
public class AnalysisCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCallbackHandler.class);

    private final AnalysisCallbackService callbackService;

    @RabbitListener(queues = "${app.messaging.rabbitmq.queues.names.core-callback-analysis}")
    public void handle(AnalysisCallbackEnvelope envelope) {
        try {
            callbackService.apply(envelope);
        } catch (RuntimeException e) {
            log.error("callback.analysis processing failed. messageId={}",
                envelope == null ? null : envelope.messageId(), e);
            throw e;
        }
    }
}
