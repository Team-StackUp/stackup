package com.stackup.stackup.document.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.document.application.dto.AnalyzeRepositoryPayload;
import com.stackup.stackup.document.application.dto.AnalyzeResumePayload;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.github.domain.GithubRepositoryRepository;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 이력서 리포 분석 트리거
// CRUD 및 상태관리 수행
// 클래스 레벨 @Transactional 은 두지 않음 — @TransactionalEventListener 메서드와 충돌 (Spring 7 제약).
@Service
@RequiredArgsConstructor
public class AnalysisRequestService {

    private final ResumeRepository resumeRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final AnalyzedDocumentRepository analyzedDocumentRepository;
    private final RabbitMessagePublisher publisher;
    private final RabbitMqProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional
    public AnalysisHandle requestResumeAnalysis(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
            .orElseThrow(() -> new IllegalArgumentException("resume not found: " + resumeId));
        if (!resume.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("resume does not belong to user");
        }
        AnalyzedDocument doc = analyzedDocumentRepository.save(AnalyzedDocument.forResume(resume));
        resume.markAnalyzing();

        events.publishEvent(new ResumeAnalysisRequestedEvent(
            userId,
            doc.getId(),
            new AnalyzeResumePayload(resume.getId(), resume.getFilePath(), doc.getId())
        ));
        return new AnalysisHandle(doc.getId(), resume.getId(), null);
    }

    @Transactional
    public AnalysisHandle requestRepositoryAnalysis(Long userId, Long repositoryId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("repository not found: " + repositoryId));
        if (!repo.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("repository does not belong to user");
        }
        AnalyzedDocument doc = analyzedDocumentRepository.save(AnalyzedDocument.forRepository(repo));
        repo.markAnalyzing();

        events.publishEvent(new RepositoryAnalysisRequestedEvent(
            userId,
            doc.getId(),
            new AnalyzeRepositoryPayload(
                repo.getId(),
                repo.getRepoFullName(),
                repo.getDefaultBranch() == null ? "main" : repo.getDefaultBranch(),
                doc.getId()
            )
        ));
        return new AnalysisHandle(doc.getId(), null, repo.getId());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResumeAnalysisRequested(ResumeAnalysisRequestedEvent event) {
        publisher.publishToAi(
            properties.routingKeys().analyzeResume(),
            event.payload(),
            new MessageContext(event.userId(), null, event.documentId(), null)
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRepositoryAnalysisRequested(RepositoryAnalysisRequestedEvent event) {
        publisher.publishToAi(
            properties.routingKeys().analyzeRepository(),
            event.payload(),
            new MessageContext(event.userId(), null, event.documentId(), event.payload().repositoryId())
        );
    }

    public record AnalysisHandle(Long analyzedDocumentId, Long resumeId, Long repositoryId) {
    }

    record ResumeAnalysisRequestedEvent(Long userId, Long documentId, AnalyzeResumePayload payload) {
    }

    record RepositoryAnalysisRequestedEvent(Long userId, Long documentId, AnalyzeRepositoryPayload payload) {
    }
}
