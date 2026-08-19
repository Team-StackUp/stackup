package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.BookmarkedQuestionResult;
import java.time.Instant;

public record BookmarkedQuestionResponse(
    Long messageId,
    Long sessionId,
    String sessionTitle,
    String category,
    String question,
    String expectedSignal,
    String myAnswer,
    String modelAnswer,
    String coachingComment,
    Instant createdAt
) {
    public static BookmarkedQuestionResponse from(BookmarkedQuestionResult r) {
        return new BookmarkedQuestionResponse(
            r.messageId(),
            r.sessionId(),
            r.sessionTitle(),
            r.category(),
            r.question(),
            r.expectedSignal(),
            r.myAnswer(),
            r.modelAnswer(),
            r.coachingComment(),
            r.createdAt()
        );
    }
}
