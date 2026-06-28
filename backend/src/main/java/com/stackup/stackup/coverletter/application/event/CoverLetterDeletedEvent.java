package com.stackup.stackup.coverletter.application.event;

// CoverLetter soft delete commit 후 발화. document 도메인이 수신해 관련 AnalyzedDocument cascade soft delete.
public record CoverLetterDeletedEvent(Long userId, Long coverLetterId) {
}
