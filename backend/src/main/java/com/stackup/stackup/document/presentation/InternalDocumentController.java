package com.stackup.stackup.document.presentation;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.document.application.DocumentEmbeddingService;
import com.stackup.stackup.document.application.dto.EmbeddingUpsertCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 서버 전용. /api/internal/documents/{documentId}/embeddings.
 * 청크 + 임베딩을 pgvector 에 idempotent upsert.
 */
@RestController
@RequestMapping("/api/internal/documents")
@RequiredArgsConstructor
public class InternalDocumentController {

    private final DocumentEmbeddingService embeddingService;

    @PutMapping("/{documentId}/embeddings")
    public UpsertResponse upsert(
        @PathVariable Long documentId,
        @Valid @RequestBody UpsertRequest request
    ) {
        for (ChunkRequest c : request.chunks()) {
            if (c.embedding() == null || c.embedding().length != request.dim()) {
                throw new DomainException(ApiErrorCode.EMBEDDING_BAD_REQUEST);
            }
        }

        List<EmbeddingUpsertCommand.Chunk> mapped = request.chunks().stream()
            .map(c -> new EmbeddingUpsertCommand.Chunk(c.chunkIndex(), c.chunkText(), c.embedding()))
            .toList();
        int upserted = embeddingService.upsert(
            new EmbeddingUpsertCommand(documentId, request.model(), request.dim(), mapped)
        );
        return new UpsertResponse(upserted);
    }

    public record UpsertRequest(
        String model,
        @Positive int dim,
        @NotNull @NotEmpty List<@Valid ChunkRequest> chunks
    ) {
    }

    public record ChunkRequest(
        int chunkIndex,
        String chunkText,
        float[] embedding
    ) {
    }

    public record UpsertResponse(int upserted) {
    }
}
