package com.stackup.stackup.document.infrastructure;

import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.domain.ResumeUsageChecker;
import org.springframework.stereotype.Component;

@Component
public class AnalyzedDocumentBasedResumeUsageChecker implements ResumeUsageChecker {

    private final AnalyzedDocumentRepository documentRepository;

    public AnalyzedDocumentBasedResumeUsageChecker(AnalyzedDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public boolean isInUse(Long resumeId) {
        return documentRepository.existsActiveSessionContextForResume(resumeId);
    }
}
