package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionsCallbackPayload(
    Long sessionId,
    String kind,                          // "FIRST" | "FOLLOWUP" | "END"
    String category,                      // FIRST / FOLLOWUP 일 때: PROJECT_DEEP_DIVE / CS_FUNDAMENTAL 등
    String question,                      // FIRST / FOLLOWUP 일 때: 질문 본문
    Long parentMessageId,                 // FOLLOWUP 일 때: 직전 답변 메시지 id
    Map<String, Object> answerEvaluation, // FOLLOWUP / END 일 때: { specificity, logic, structure }
    Map<String, Object> voiceAnalysis     // 음성 모드, 텍스트 면접에선 null
) {
    public boolean isFirst()    { return "FIRST".equals(kind); }
    public boolean isFollowup() { return "FOLLOWUP".equals(kind); }
    public boolean isEnd()      { return "END".equals(kind); }
}
