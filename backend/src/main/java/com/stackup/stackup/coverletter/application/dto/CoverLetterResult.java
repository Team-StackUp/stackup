package com.stackup.stackup.coverletter.application.dto;

import com.stackup.stackup.coverletter.domain.CoverLetter;
import com.stackup.stackup.coverletter.domain.CoverLetterStatus;
import java.time.Instant;
import java.util.List;

public record CoverLetterResult(
    Long id,
    String title,
    List<CoverLetterItem> items,
    CoverLetterStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static CoverLetterResult of(CoverLetter coverLetter, List<CoverLetterItem> items) {
        return new CoverLetterResult(
            coverLetter.getId(),
            coverLetter.getTitle(),
            items,
            coverLetter.getStatus(),
            coverLetter.getCreatedAt(),
            coverLetter.getUpdatedAt()
        );
    }
}
