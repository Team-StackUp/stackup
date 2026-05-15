package com.stackup.stackup.resume.application.event;

public record ResumeUploadedEvent(Long resumeId, Long userId, String s3Key, String traceId) {
}
