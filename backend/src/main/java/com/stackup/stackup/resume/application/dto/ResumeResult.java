package com.stackup.stackup.resume.application.dto;

import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeStatus;
import java.time.Instant;

public record ResumeResult(
    Long id,
    String originalFilename,
    String filePath,
    ResumeFileType fileType,
    Long fileSize,
    ResumeStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static ResumeResult of(Resume resume) {
        return new ResumeResult(
            resume.getId(),
            resume.getOriginalFilename(),
            resume.getFilePath(),
            resume.getFileType(),
            resume.getFileSize(),
            resume.getStatus(),
            resume.getCreatedAt(),
            resume.getUpdatedAt()
        );
    }
}
