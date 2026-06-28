package com.stackup.stackup.coverletter.presentation.dto;

import com.stackup.stackup.coverletter.application.dto.CoverLetterResult;
import com.stackup.stackup.coverletter.domain.CoverLetterStatus;
import java.time.Instant;
import java.util.List;

public record CoverLetterResponse(
    Long id,
    String title,
    List<Item> items,
    CoverLetterStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public record Item(String question, String answer) {
    }

    public static CoverLetterResponse from(CoverLetterResult result) {
        List<Item> items = result.items().stream()
            .map(i -> new Item(i.question(), i.answer()))
            .toList();
        return new CoverLetterResponse(
            result.id(),
            result.title(),
            items,
            result.status(),
            result.createdAt(),
            result.updatedAt()
        );
    }
}
