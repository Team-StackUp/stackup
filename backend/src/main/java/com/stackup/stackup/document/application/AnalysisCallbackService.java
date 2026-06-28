package com.stackup.stackup.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import org.springframework.context.ApplicationEventPublisher;
import com.stackup.stackup.coverletter.domain.CoverLetter;
import com.stackup.stackup.document.application.dto.AnalysisCallbackEnvelope;
import com.stackup.stackup.document.application.dto.AnalysisCallbackPayload;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.resume.domain.Resume;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisCallbackService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCallbackService.class);
    private static final String CONSUMER_NAME = "core.callback.analysis";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AnalyzedDocumentRepository documentRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void apply(AnalysisCallbackEnvelope envelope) {
        if (envelope == null || envelope.payload() == null) {
            log.warn("callback.analysis envelope or payload is null — skip");
            return;
        }
        AnalysisCallbackPayload payload = envelope.payload();
        if (isProcessed(envelope.messageId())) {
            log.info("callback.analysis duplicate, skip. messageId={}", envelope.messageId());
            return;
        }
        log.info("callback.analysis received. messageId={}, targetType={}, targetId={}, status={}, traceId={}",
            envelope.messageId(), payload.targetType(), payload.targetId(), payload.status(), envelope.traceId());

        Long documentId = envelope.context() == null ? null : envelope.context().documentId();
        if (documentId == null) {
            log.warn("callback.analysis missing documentId in context. messageId={}", envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }

        AnalyzedDocument doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.warn("callback.analysis document not found. id={}, messageId={}", documentId, envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }

        if (payload.isAnalyzed()) {
            applyAnalyzed(doc, payload);
        } else if (payload.isFailed()) {
            applyFailed(doc, payload);
        } else {
            log.warn("callback.analysis unknown status={}, treat as FAILED. messageId={}",
                payload.status(), envelope.messageId());
            applyFailed(doc, payload);
        }
        publishSse(doc, payload);
        markProcessed(envelope.messageId());
    }

    private void applyAnalyzed(AnalyzedDocument doc, AnalysisCallbackPayload payload) {
        String techStackJson = serializeTechStack(payload.techStack());
        int chunkCount = payload.embeddingChunkCount() == null ? 0 : payload.embeddingChunkCount();
        doc.markAnalyzed(payload.documentPath(), payload.summary(), techStackJson, chunkCount);

        Resume resume = doc.getResume();
        if (resume != null) {
            resume.markAnalyzed();
        }
        GithubRepository repo = doc.getRepository();
        if (repo != null) {
            repo.markAnalyzed();
        }
        CoverLetter coverLetter = doc.getCoverLetter();
        if (coverLetter != null) {
            coverLetter.markAnalyzed();
        }
    }

    private void applyFailed(AnalyzedDocument doc, AnalysisCallbackPayload payload) {
        doc.markFailed(payload.errorCode(), payload.errorMessage());
        Resume resume = doc.getResume();
        if (resume != null) {
            resume.markFailed();
        }
        GithubRepository repo = doc.getRepository();
        if (repo != null) {
            repo.markFailed();
        }
        CoverLetter coverLetter = doc.getCoverLetter();
        if (coverLetter != null) {
            coverLetter.markFailed();
        }
    }

    private void publishSse(AnalyzedDocument doc, AnalysisCallbackPayload payload) {
        SseEventType type = doc.getRepository() != null ? SseEventType.REPO_STATE : SseEventType.DOC_STATE;
        events.publishEvent(RealtimeNotifyEvent.document(doc.getId(), type, payload));
        // 프론트가 documentId 를 모르고도 /realtime/stream/me 로 분석 진행을 받을 수 있게 user 채널에도 push.
        Long userId = resolveOwnerUserId(doc);
        if (userId != null) {
            events.publishEvent(RealtimeNotifyEvent.user(userId, type, payload));
        }
    }

    private Long resolveOwnerUserId(AnalyzedDocument doc) {
        if (doc.getResume() != null && doc.getResume().getUser() != null) {
            return doc.getResume().getUser().getId();
        }
        if (doc.getRepository() != null && doc.getRepository().getUser() != null) {
            return doc.getRepository().getUser().getId();
        }
        if (doc.getCoverLetter() != null && doc.getCoverLetter().getUser() != null) {
            return doc.getCoverLetter().getUser().getId();
        }
        return null;
    }

    private boolean isProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        return processedMessageRepository.existsById(messageId);
    }

    private void markProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        try {
            processedMessageRepository.save(ProcessedMessage.of(messageId, CONSUMER_NAME));
        } catch (DataIntegrityViolationException ignored) {
            // race: 다른 worker 가 동시에 INSERT. 둘 다 같은 작업 처리 → 무시.
        }
    }

    private String serializeTechStack(List<String> techStack) {
        if (techStack == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(techStack);
        } catch (JsonProcessingException e) {
            log.warn("techStack serialize failed, store as empty array", e);
            return "[]";
        }
    }
}
