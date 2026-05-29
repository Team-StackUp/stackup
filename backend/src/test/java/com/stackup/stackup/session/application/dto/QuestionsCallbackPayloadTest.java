package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionsCallbackPayloadTest {
    private final ObjectMapper m = JsonMapper.builder().findAndAddModules().build();

    @Test
    void first_payload_round_trip() throws Exception {
        String json = """
            {"sessionId":99,"kind":"FIRST",
             "category":"PROJECT_DEEP_DIVE","question":"Q1"}""";
        QuestionsCallbackPayload p = m.readValue(json, QuestionsCallbackPayload.class);
        assertThat(p.isFirst()).isTrue();
        assertThat(p.category()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(p.question()).isEqualTo("Q1");
    }

    @Test
    void followup_payload_round_trip() throws Exception {
        String json = """
            {"sessionId":99,"kind":"FOLLOWUP","parentMessageId":502,
             "category":"CS_FUNDAMENTAL","question":"왜?",
             "answerEvaluation":{"specificity":3.5}}""";
        QuestionsCallbackPayload p = m.readValue(json, QuestionsCallbackPayload.class);
        assertThat(p.isFollowup()).isTrue();
        assertThat(p.parentMessageId()).isEqualTo(502L);
        assertThat(p.question()).isEqualTo("왜?");
    }

    @Test
    void end_payload_round_trip() throws Exception {
        String json = """
            {"sessionId":99,"kind":"END",
             "answerEvaluation":{"specificity":4.1}}""";
        QuestionsCallbackPayload p = m.readValue(json, QuestionsCallbackPayload.class);
        assertThat(p.isEnd()).isTrue();
        assertThat(p.question()).isNull();
    }
}
