package com.stackup.stackup.document.infrastructure;

import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.domain.ResumeUsageChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalyzedDocumentBasedResumeUsageChecker implements ResumeUsageChecker {

    private final AnalyzedDocumentRepository documentRepository;

    @Override
    public boolean isInUse(Long resumeId) {
        return documentRepository.existsActiveSessionContextForResume(resumeId);
    }
}
