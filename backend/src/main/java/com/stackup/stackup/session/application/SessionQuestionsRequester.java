package com.stackup.stackup.session.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.common.storage.StorageException;
import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.GenerateQuestionsPayload;
import com.stackup.stackup.session.application.dto.GenerateQuestionsPayload.DocumentContext;
import com.stackup.stackup.session.application.event.SessionCreatedEvent;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionQuestionPoolRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 세션 생성 commit 후 발화 → generate.questions envelope 발행.
// AI 가 documentIds 만으로 다시 fetch 하지 않도록, MD 본문까지 envelope 에 담아 보낸다 (RAG 정교화는 후속).
@Component
@RequiredArgsConstructor
public class SessionQuestionsRequester {

    private static final Logger log = LoggerFactory.getLogger(SessionQuestionsRequester.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> TECH_STACK_TYPE = new TypeReference<>() {};
    private static final long MAX_MARKDOWN_BYTES = 200_000L;  // envelope 비대화 방지
    private static final int DEFAULT_GENERAL_QUESTION_COUNT = 3;

    private final RabbitMessagePublisher publisher;
    private final RabbitMqProperties properties;
    private final AnalyzedDocumentRepository documentRepository;
    private final ObjectStorageClient storage;
    private final InterviewSessionRepository sessionRepository;
    private final SessionQuestionPoolRepository questionPoolRepository;

    // 최근 몇 개 세션까지 거슬러 중복 질문을 회피할지. 0 이면 비활성.
    @Value("${interview.question-dedup.recent-sessions:3}")
    private int recentSessionCount;
    // AI 에 넘길 과거 질문 최대 개수 (envelope 비대화 방지).
    @Value("${interview.question-dedup.max-questions:30}")
    private int maxRecentQuestions;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionCreated(SessionCreatedEvent event) {
        List<DocumentContext> documents = buildDocumentContexts(event.contextDocumentIds());
        int generalCount = event.generalQuestionCount() != null
            ? event.generalQuestionCount() : DEFAULT_GENERAL_QUESTION_COUNT;
        List<String> recentQuestions = recentQuestions(event.userId(), event.sessionId());
        GenerateQuestionsPayload payload = new GenerateQuestionsPayload(
            event.sessionId(),
            event.mode(),
            event.jobCategories(),
            documents,
            generalCount,
            event.maxQuestions(),
            recentQuestions
        );
        publisher.publishToAi(
            properties.routingKeys().generateQuestions(),
            payload,
            new MessageContext(event.userId(), event.sessionId(), null, null)
        );
        log.info("generate.questions published. sessionId={}, doc_count={}, general_count={}, max={}, recent_q={}",
            event.sessionId(), documents.size(), generalCount, event.maxQuestions(), recentQuestions.size());
    }

    // 같은 유저의 최근 N개 세션에서 출제된 질문을 모아 AI 의 중복 회피용으로 전달.
    private List<String> recentQuestions(Long userId, Long currentSessionId) {
        if (recentSessionCount <= 0 || maxRecentQuestions <= 0) {
            return List.of();
        }
        List<Long> sessionIds = sessionRepository.findRecentSessionIds(
            userId, currentSessionId, PageRequest.of(0, recentSessionCount));
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return questionPoolRepository.findRecentQuestions(
            sessionIds, PageRequest.of(0, maxRecentQuestions));
    }

    private List<DocumentContext> buildDocumentContexts(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        List<DocumentContext> result = new ArrayList<>(documentIds.size());
        for (Long id : documentIds) {
            AnalyzedDocument doc = documentRepository.findById(id).orElse(null);
            if (doc == null || doc.getAnalysisStatus() != AnalysisStatus.ANALYZED) {
                continue;  // 미완료 문서는 컨텍스트에서 skip (SessionService 가 이미 검증 — 방어)
            }
            result.add(new DocumentContext(
                doc.getId(),
                resolveSourceType(doc),
                doc.getSummary(),
                parseTechStack(doc.getTechStack()),
                fetchMarkdown(doc.getDocumentPath())
            ));
        }
        return result;
    }

    private String resolveSourceType(AnalyzedDocument doc) {
        if (doc.getResume() != null) return "RESUME";
        if (doc.getRepository() != null) return "REPOSITORY";
        return "UNKNOWN";
    }

    private List<String> parseTechStack(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, TECH_STACK_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("techStack parse failed during generate.questions publish", e);
            return List.of();
        }
    }

    private String fetchMarkdown(String documentPath) {
        if (documentPath == null || documentPath.isBlank()) {
            return null;
        }
        try (InputStream in = storage.get(documentPath)) {
            byte[] bytes = in.readNBytes((int) MAX_MARKDOWN_BYTES);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (StorageException | IOException e) {
            log.warn("markdown fetch failed for generate.questions. key={}", documentPath, e);
            return null;
        }
    }
}
