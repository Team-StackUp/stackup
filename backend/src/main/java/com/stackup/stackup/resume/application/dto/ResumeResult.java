package com.stackup.stackup.resume.application.dto;

import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeStatus;
import java.time.Instant;

public record ResumeResult(
    Long id,
    String originalFilename,
    Long fileSize,
    ResumeStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static ResumeResult from(Resume resume) {
        return new ResumeResult(
            resume.getId(),
            resume.getOriginalFilename(),
            resume.getFileSize(),
            resume.getStatus(),
            resume.getCreatedAt(),
            resume.getUpdatedAt()
        );
    }
}
