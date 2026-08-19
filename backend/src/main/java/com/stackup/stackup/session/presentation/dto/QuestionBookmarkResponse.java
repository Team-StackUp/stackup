package com.stackup.stackup.session.presentation.dto;

public record QuestionBookmarkResponse(
    Long messageId,
    boolean bookmarked
) {
}
