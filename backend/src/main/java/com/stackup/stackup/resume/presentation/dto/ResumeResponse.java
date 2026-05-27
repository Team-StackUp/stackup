package com.stackup.stackup.resume.presentation.dto;

import com.stackup.stackup.resume.application.dto.ResumeResult;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeStatus;
import java.time.Instant;

public record ResumeResponse(
    Long id,
    String originalFilename,
    String filePath,
    ResumeFileType fileType,
    Long fileSize,
    ResumeStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static ResumeResponse from(ResumeResult result) {
        return new ResumeResponse(
            result.id(),
            result.originalFilename(),
            result.filePath(),
            result.fileType(),
            result.fileSize(),
            result.status(),
            result.createdAt(),
            result.updatedAt()
        );
    }
}
