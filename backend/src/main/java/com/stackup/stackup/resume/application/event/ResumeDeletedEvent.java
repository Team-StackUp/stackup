package com.stackup.stackup.resume.application.event;

// Resume soft delete commit 후 발화. document 도메인이 수신해 관련 AnalyzedDocument cascade soft delete.
public record ResumeDeletedEvent(Long userId, Long resumeId) {
}
