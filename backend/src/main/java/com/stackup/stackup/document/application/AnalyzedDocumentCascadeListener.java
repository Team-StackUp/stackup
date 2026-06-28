package com.stackup.stackup.document.application;

import com.stackup.stackup.coverletter.application.event.CoverLetterDeletedEvent;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.github.application.event.RepositoryDeletedEvent;
import com.stackup.stackup.resume.application.event.ResumeDeletedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Resume / GithubRepository soft delete 발생 시 관련 AnalyzedDocument 도 cascade soft delete.
// 도메인 cycle 회피 — resume/github 가 document 도메인을 직접 import 하지 않고 이벤트로 위임.
@Component
@RequiredArgsConstructor
public class AnalyzedDocumentCascadeListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyzedDocumentCascadeListener.class);

    private final AnalyzedDocumentRepository analyzedDocumentRepository;

    @EventListener
    @Transactional
    public void on(ResumeDeletedEvent event) {
        List<AnalyzedDocument> docs = analyzedDocumentRepository
            .findActiveByResumeIdAndOwner(event.resumeId(), event.userId());
        for (AnalyzedDocument doc : docs) {
            doc.markDeleted();
        }
        if (!docs.isEmpty()) {
            log.info("AnalyzedDocument cascade soft delete (resume). userId={}, resumeId={}, count={}",
                event.userId(), event.resumeId(), docs.size());
        }
    }

    @EventListener
    @Transactional
    public void on(RepositoryDeletedEvent event) {
        List<AnalyzedDocument> docs = analyzedDocumentRepository
            .findActiveByRepositoryIdAndOwner(event.repositoryId(), event.userId());
        for (AnalyzedDocument doc : docs) {
            doc.markDeleted();
        }
        if (!docs.isEmpty()) {
            log.info("AnalyzedDocument cascade soft delete (repository). userId={}, repositoryId={}, count={}",
                event.userId(), event.repositoryId(), docs.size());
        }
    }

    @EventListener
    @Transactional
    public void on(CoverLetterDeletedEvent event) {
        List<AnalyzedDocument> docs = analyzedDocumentRepository
            .findActiveByCoverLetterIdAndOwner(event.coverLetterId(), event.userId());
        for (AnalyzedDocument doc : docs) {
            doc.markDeleted();
        }
        if (!docs.isEmpty()) {
            log.info("AnalyzedDocument cascade soft delete (cover letter). userId={}, coverLetterId={}, count={}",
                event.userId(), event.coverLetterId(), docs.size());
        }
    }
}
