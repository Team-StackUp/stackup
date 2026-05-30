package com.stackup.stackup.document.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.document.application.dto.EmbeddingSearchCommand;
import com.stackup.stackup.document.application.dto.EmbeddingSearchResult;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentEmbeddingSearchService {

    private static final int DEFAULT_TOP_K = 8;
    private static final int MAX_TOP_K = 20;
    private static final int EMBEDDING_DIMENSION = 1536;

    private final DocumentEmbeddingRepository embeddingRepository;

    public List<EmbeddingSearchResult> search(EmbeddingSearchCommand command) {
        SearchCriteria criteria = validate(command);

        if (!embeddingRepository.existsActiveSession(criteria.sessionId())) {
            throw new DomainException(ApiErrorCode.SESSION_NOT_FOUND);
        }

        int searchableDocumentCount = embeddingRepository.countSearchableSessionDocuments(
                criteria.sessionId(),
                criteria.documentIds()
        );
        if (searchableDocumentCount != criteria.documentIds().size()) {
            throw new DomainException(ApiErrorCode.SESSION_FORBIDDEN);
        }

        return embeddingRepository.search(
                        criteria.sessionId(),
                        criteria.documentIds(),
                        criteria.queryEmbedding(),
                        criteria.topK(),
                        criteria.minScore()
                )
                .stream()
                .map(row -> new EmbeddingSearchResult(
                        row.documentId(),
                        row.chunkIndex(),
                        row.chunkText(),
                        row.score(),
                        row.model()
                ))
                .toList();
    }

    private SearchCriteria validate(EmbeddingSearchCommand command) {
        if (command == null || command.sessionId() == null) {
            throw new DomainException(ApiErrorCode.EMBEDDING_BAD_REQUEST);
        }
        if (command.documentIds() == null || command.documentIds().isEmpty()) {
            throw new DomainException(ApiErrorCode.EMBEDDING_BAD_REQUEST);
        }
        if (command.queryEmbedding() == null || command.queryEmbedding().length == 0) {
            throw new DomainException(ApiErrorCode.EMBEDDING_BAD_REQUEST);
        }
        if (command.queryEmbedding().length != EMBEDDING_DIMENSION) {
            throw new DomainException(ApiErrorCode.EMBEDDING_BAD_REQUEST);
        }
        if (command.minScore() != null && (command.minScore() < 0.0 || command.minScore() > 1.0)) {
            throw new DomainException(ApiErrorCode.EMBEDDING_BAD_REQUEST);
        }

        int topK = command.topK() == null ? DEFAULT_TOP_K : command.topK();
        if (topK <= 0 || topK > MAX_TOP_K) {
            throw new DomainException(ApiErrorCode.EMBEDDING_BAD_REQUEST);
        }

        List<Long> documentIds = command.documentIds()
                .stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
        if (documentIds.isEmpty()) {
            throw new DomainException(ApiErrorCode.EMBEDDING_BAD_REQUEST);
        }

        return new SearchCriteria(
                command.sessionId(),
                documentIds,
                command.queryEmbedding(),
                topK,
                command.minScore()
        );
    }

    private record SearchCriteria(
            long sessionId,
            List<Long> documentIds,
            float[] queryEmbedding,
            int topK,
            Double minScore
    ) {
    }
}
