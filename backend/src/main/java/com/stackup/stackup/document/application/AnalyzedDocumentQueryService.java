package com.stackup.stackup.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.common.storage.StorageException;
import com.stackup.stackup.document.application.dto.AnalyzedDocumentResult;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyzedDocumentQueryService {

    private static final Logger log = LoggerFactory.getLogger(AnalyzedDocumentQueryService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> TECH_STACK_TYPE = new TypeReference<>() {};
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(10);

    private final AnalyzedDocumentRepository documentRepository;
    private final ObjectStorageClient storage;

    public List<AnalyzedDocumentResult> listForUser(Long userId, Long resumeId, Long repositoryId) {
        List<AnalyzedDocument> rows;
        if (resumeId != null) {
            rows = documentRepository.findActiveByResumeIdAndOwner(resumeId, userId);
        } else if (repositoryId != null) {
            rows = documentRepository.findActiveByRepositoryIdAndOwner(repositoryId, userId);
        } else {
            rows = documentRepository.findActiveByOwner(userId);
        }
        return rows.stream()
            .map(doc -> AnalyzedDocumentResult.of(doc, parseTechStack(doc.getTechStack()), null))
            .toList();
    }

    public AnalyzedDocumentResult getForUser(Long userId, Long documentId) {
        AnalyzedDocument doc = documentRepository.findActiveByIdAndOwner(documentId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.DOC_NOT_FOUND));
        URI downloadUrl = presignedDownloadUrl(doc.getDocumentPath());
        return AnalyzedDocumentResult.of(doc, parseTechStack(doc.getTechStack()), downloadUrl);
    }

    private List<String> parseTechStack(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, TECH_STACK_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("techStack parse failed, return empty. raw={}", json, e);
            return List.of();
        }
    }

    private URI presignedDownloadUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return storage.createPresignedGetUrl(key, DOWNLOAD_URL_TTL);
        } catch (StorageException e) {
            log.warn("presigned URL creation failed. key={}", key, e);
            return null;
        }
    }
}
