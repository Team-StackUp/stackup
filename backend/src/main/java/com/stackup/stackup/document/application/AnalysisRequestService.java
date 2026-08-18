package com.stackup.stackup.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.coverletter.domain.CoverLetter;
import com.stackup.stackup.coverletter.domain.CoverLetterRepository;
import com.stackup.stackup.document.application.dto.AnalyzeCoverLetterPayload;
import com.stackup.stackup.document.application.dto.AnalyzeRepositoryPayload;
import com.stackup.stackup.document.application.dto.AnalyzeResumePayload;
import com.stackup.stackup.document.application.dto.AnalyzeWebPayload;
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

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ResumeRepository resumeRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final CoverLetterRepository coverLetterRepository;
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

    // 웹 이력서(URL) 분석. PDF 와 같은 Resume 행·AnalyzedDocument 를 쓰고 payload 만 URL 기반이다.
    @Transactional
    public AnalysisHandle requestWebResumeAnalysis(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
            .orElseThrow(() -> new IllegalArgumentException("resume not found: " + resumeId));
        if (!resume.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("resume does not belong to user");
        }
        if (resume.getSourceUrl() == null || resume.getSourceUrl().isBlank()) {
            throw new IllegalArgumentException("web resume has no source url: " + resumeId);
        }
        AnalyzedDocument doc = analyzedDocumentRepository.save(AnalyzedDocument.forResume(resume));
        resume.markAnalyzing();

        events.publishEvent(new WebResumeAnalysisRequestedEvent(
            userId,
            doc.getId(),
            new AnalyzeWebPayload(resume.getId(), resume.getSourceUrl(), doc.getId())
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

    @Transactional
    public AnalysisHandle requestCoverLetterAnalysis(Long userId, Long coverLetterId) {
        CoverLetter coverLetter = coverLetterRepository.findById(coverLetterId)
            .orElseThrow(() -> new IllegalArgumentException("cover letter not found: " + coverLetterId));
        if (!coverLetter.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("cover letter does not belong to user");
        }
        AnalyzedDocument doc = analyzedDocumentRepository.save(AnalyzedDocument.forCoverLetter(coverLetter));
        coverLetter.markAnalyzing();

        String content = buildMarkdown(coverLetter.getTitle(), coverLetter.getItems());
        events.publishEvent(new CoverLetterAnalysisRequestedEvent(
            userId,
            doc.getId(),
            new AnalyzeCoverLetterPayload(coverLetter.getId(), content, doc.getId())
        ));
        return new AnalysisHandle(doc.getId(), null, null);
    }

    // 문항 JSON([{question, answer}]) → 분석·임베딩용 마크다운. 문항 구조를 보존해 질문 생성의 근거가 되게 한다.
    private String buildMarkdown(String title, String itemsJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 자기소개서");
        if (title != null && !title.isBlank()) {
            sb.append(" — ").append(title.trim());
        }
        sb.append("\n\n");
        try {
            JsonNode root = JSON.readTree(itemsJson == null ? "[]" : itemsJson);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    String question = item.path("question").asText("").trim();
                    String answer = item.path("answer").asText("").trim();
                    if (answer.isEmpty()) {
                        continue;
                    }
                    sb.append("## ").append(question.isEmpty() ? "문항" : question).append("\n");
                    sb.append(answer).append("\n\n");
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 파싱 실패 시 원문이라도 분석에 넘긴다(빈 분석 방지).
            sb.append(itemsJson == null ? "" : itemsJson);
        }
        return sb.toString();
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
    public void onWebResumeAnalysisRequested(WebResumeAnalysisRequestedEvent event) {
        publisher.publishToAi(
            properties.routingKeys().analyzeWeb(),
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCoverLetterAnalysisRequested(CoverLetterAnalysisRequestedEvent event) {
        publisher.publishToAi(
            properties.routingKeys().analyzeCoverLetter(),
            event.payload(),
            new MessageContext(event.userId(), null, event.documentId(), null)
        );
    }

    public record AnalysisHandle(Long analyzedDocumentId, Long resumeId, Long repositoryId) {
    }

    record ResumeAnalysisRequestedEvent(Long userId, Long documentId, AnalyzeResumePayload payload) {
    }

    record WebResumeAnalysisRequestedEvent(Long userId, Long documentId, AnalyzeWebPayload payload) {
    }

    record RepositoryAnalysisRequestedEvent(Long userId, Long documentId, AnalyzeRepositoryPayload payload) {
    }

    record CoverLetterAnalysisRequestedEvent(Long userId, Long documentId, AnalyzeCoverLetterPayload payload) {
    }
}
