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
import com.stackup.stackup.session.application.event.SelfIntroAnsweredEvent;
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

// 모든 면접의 첫 질문은 자기소개로 고정한다.
// - 세션 생성 commit 후: 자기소개 질문(seq=1)만 삽입(QuestionsCallbackService 위임). 이 단계에선 풀 생성을 하지 않는다.
// - 자기소개 답변 commit 후: 그 답변을 씨앗으로 generate.questions envelope 발행 → 이력서/레포 기반 질문 풀 생성.
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
    private final QuestionsCallbackService questionsCallbackService;

    // 최근 몇 개 세션까지 거슬러 중복 질문을 회피할지. 0 이면 비활성.
    @Value("${interview.question-dedup.recent-sessions:3}")
    private int recentSessionCount;
    // AI 에 넘길 과거 질문 최대 개수 (envelope 비대화 방지).
    @Value("${interview.question-dedup.max-questions:30}")
    private int maxRecentQuestions;

    // 세션 생성 직후: 자기소개 질문을 고정 삽입한다(AI 호출 없음). 질문 풀 생성은 답변 이후로 미룬다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionCreated(SessionCreatedEvent event) {
        questionsCallbackService.insertSelfIntroduction(event.sessionId());
    }

    // 자기소개 답변 직후: 그 답변 + 이력서/레포 컨텍스트로 질문 풀 생성을 요청한다.
    // 자기소개가 첫 질문 1자리를 차지하므로, 풀은 generalCount-1 개만 생성한다(총 일반질문 수 보존).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSelfIntroAnswered(SelfIntroAnsweredEvent event) {
        List<DocumentContext> documents = buildDocumentContexts(event.contextDocumentIds());
        int generalCount = event.generalQuestionCount() != null
            ? event.generalQuestionCount() : DEFAULT_GENERAL_QUESTION_COUNT;
        int poolCount = Math.max(1, generalCount - 1);  // 자기소개 1자리 예약
        List<String> recentQuestions = recentQuestions(event.userId(), event.sessionId());
        GenerateQuestionsPayload payload = new GenerateQuestionsPayload(
            event.sessionId(),
            event.mode(),
            event.jobCategories(),
            documents,
            poolCount,
            event.maxQuestions(),
            recentQuestions,
            event.selfIntroAnswer()
        );
        publisher.publishToAi(
            properties.routingKeys().generateQuestions(),
            payload,
            new MessageContext(event.userId(), event.sessionId(), null, null)
        );
        log.info("generate.questions published (post self-intro). sessionId={}, doc_count={}, "
                + "pool_count={}, max={}, recent_q={}, intro_len={}",
            event.sessionId(), documents.size(), poolCount, event.maxQuestions(), recentQuestions.size(),
            event.selfIntroAnswer() == null ? 0 : event.selfIntroAnswer().length());
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
