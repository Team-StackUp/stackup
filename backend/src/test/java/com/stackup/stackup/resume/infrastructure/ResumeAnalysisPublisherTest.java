package com.stackup.stackup.resume.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.resume.application.event.ResumeUploadedEvent;
import com.stackup.stackup.resume.infrastructure.dto.AnalyzeResumePayload;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ResumeAnalysisPublisherTest {

    @Test
    void delegates_to_rabbit_message_publisher_with_analyze_resume_routing_key() {
        RabbitMessagePublisher rabbitPublisher = Mockito.mock(RabbitMessagePublisher.class);
        RabbitMqProperties props = Mockito.mock(RabbitMqProperties.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(props.routingKeys().analyzeResume()).thenReturn("analyze.resume");

        ResumeAnalysisPublisher publisher = new ResumeAnalysisPublisher(rabbitPublisher, props);

        publisher.handle(new ResumeUploadedEvent(7L, 42L, "resumes/raw/42/abc.pdf"));

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Map> contextCaptor = ArgumentCaptor.forClass(Map.class);

        verify(rabbitPublisher).publishToAi(routingKeyCaptor.capture(), payloadCaptor.capture(), contextCaptor.capture());

        assertThat(routingKeyCaptor.getValue()).isEqualTo("analyze.resume");
        assertThat(payloadCaptor.getValue()).isInstanceOf(AnalyzeResumePayload.class);
        AnalyzeResumePayload payload = (AnalyzeResumePayload) payloadCaptor.getValue();
        assertThat(payload.resumeId()).isEqualTo(7L);
        assertThat(payload.s3Key()).isEqualTo("resumes/raw/42/abc.pdf");
        assertThat(contextCaptor.getValue()).containsEntry("userId", 42L);
    }
}
