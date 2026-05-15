package com.stackup.stackup.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageEnvelopeTest {

    @Test
    void serialized_envelope_has_required_fields() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        MessageEnvelope envelope = new MessageEnvelope(
            "msg-1",
            "analyze.resume",
            "v1",
            "trace-1",
            Instant.parse("2026-05-15T10:00:00Z"),
            "core-server",
            Map.of("resumeId", 42, "s3Key", "resumes/raw/1/abc.pdf"),
            Map.of("userId", 1)
        );

        String json = mapper.writeValueAsString(envelope);

        assertThat(json).contains("\"messageId\":\"msg-1\"");
        assertThat(json).contains("\"messageType\":\"analyze.resume\"");
        assertThat(json).contains("\"version\":\"v1\"");
        assertThat(json).contains("\"traceId\":\"trace-1\"");
        assertThat(json).contains("\"publishedAt\":\"2026-05-15T10:00:00Z\"");
        assertThat(json).contains("\"publisher\":\"core-server\"");
    }
}
