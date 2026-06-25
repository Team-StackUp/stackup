package com.stackup.stackup.session.application.dto;

// callback.feedback 의 답변별 복기 1건 (AI → Core). 해당 답변(INTERVIEWEE) 메시지에 기록된다.
public record AnswerCoachingItem(
    Long messageId,
    String modelAnswer,
    String answerRewrite,
    String coachingComment
) {
}
