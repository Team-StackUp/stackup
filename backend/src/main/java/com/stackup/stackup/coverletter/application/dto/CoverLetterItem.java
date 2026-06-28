package com.stackup.stackup.coverletter.application.dto;

// 자소서 문항 한 개 (질문 + 답변).
public record CoverLetterItem(
    String question,
    String answer
) {
}
