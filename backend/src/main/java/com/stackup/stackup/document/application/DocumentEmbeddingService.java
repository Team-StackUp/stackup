package com.stackup.stackup.document.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.document.application.dto.EmbeddingUpsertCommand;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository.EmbeddingChunk;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentEmbeddingService {

    private final AnalyzedDocumentRepository documentRepository;
    private final DocumentEmbeddingRepository embeddingRepository;

    @Transactional
    public int upsert(EmbeddingUpsertCommand command) {
        if (!documentRepository.existsById(command.documentId())) {
            throw new DomainException(ApiErrorCode.DOC_NOT_FOUND);
        }
        List<EmbeddingChunk> mapped = command.chunks().stream()
            .map(c -> new EmbeddingChunk(c.chunkIndex(), c.chunkText(), c.embedding()))
            .toList();
        return embeddingRepository.upsertAll(command.documentId(), command.model(), mapped);
    }
}
