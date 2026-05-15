package com.stackup.stackup.resume.presentation.dto;

import com.stackup.stackup.resume.application.dto.ResumeResult;
import com.stackup.stackup.resume.domain.ResumeStatus;
import java.time.Instant;

public record ResumeResponse(
    Long id,
    String originalFilename,
    Long fileSize,
    ResumeStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static ResumeResponse from(ResumeResult r) {
        return new ResumeResponse(r.id(), r.originalFilename(), r.fileSize(), r.status(), r.createdAt(), r.updatedAt());
    }
}
