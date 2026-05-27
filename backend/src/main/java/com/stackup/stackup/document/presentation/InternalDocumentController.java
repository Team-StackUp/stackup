package com.stackup.stackup.document.presentation;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.document.application.DocumentEmbeddingService;
import com.stackup.stackup.document.application.dto.EmbeddingUpsertCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Internal: Document Embeddings", description = "X-Internal-API-Key 필요. AI 서버가 분석 결과 청크/임베딩을 pgvector 에 idempotent upsert 할 때 호출.")
@RestController
@RequestMapping("/api/internal/documents")
@RequiredArgsConstructor
public class InternalDocumentController {

    private final DocumentEmbeddingService embeddingService;

    @Operation(
        operationId = "internalUpsertDocumentEmbeddings",
        summary = "분석 문서 청크 + 임베딩 upsert",
        description = "(document_id, chunk_index) 기준 idempotent. 벡터 차원이 dim 과 다르면 400."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "upsert 성공 (응답에 upsert 된 개수 포함)"),
        @ApiResponse(responseCode = "400", description = "임베딩 페이로드 검증 실패 (dim 불일치 등)"),
        @ApiResponse(responseCode = "401", description = "X-Internal-API-Key 인증 실패"),
        @ApiResponse(responseCode = "404", description = "documentId 에 해당하는 analyzed_documents 없음")
    })
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
