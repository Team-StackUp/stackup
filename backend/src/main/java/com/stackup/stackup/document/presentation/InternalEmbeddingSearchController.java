package com.stackup.stackup.document.presentation;

import com.stackup.stackup.document.application.DocumentEmbeddingService;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository.SearchHit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal: Embedding Search",
    description = "X-Internal-API-Key 필요. AI 서버가 질문/피드백 생성 시 컨텍스트 청크 검색 (pgvector cosine).")
@RestController
@RequestMapping("/api/internal/embeddings")
@RequiredArgsConstructor
public class InternalEmbeddingSearchController {

    private final DocumentEmbeddingService embeddingService;

    @Operation(
        operationId = "internalSearchEmbeddings",
        summary = "pgvector cosine topK 검색",
        description = "queryEmbedding 으로 가장 가까운 청크 topK 반환. documentIds 가 비어 있으면 전체."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "검색 결과"),
        @ApiResponse(responseCode = "400", description = "queryEmbedding 누락"),
        @ApiResponse(responseCode = "401", description = "X-Internal-API-Key 인증 실패")
    })
    @PostMapping("/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        List<SearchHit> hits = embeddingService.search(
            request.queryEmbedding(),
            request.documentIds() == null ? List.of() : request.documentIds(),
            request.topK() == null ? 5 : request.topK()
        );
        return new SearchResponse(hits.stream().map(SearchResponseHit::from).toList());
    }

    public record SearchRequest(
        @NotNull float[] queryEmbedding,
        List<Long> documentIds,
        @Positive Integer topK
    ) {
    }

    public record SearchResponse(List<SearchResponseHit> hits) {
    }

    public record SearchResponseHit(
        long documentId,
        int chunkIndex,
        String chunkText,
        double distance
    ) {
        public static SearchResponseHit from(SearchHit h) {
            return new SearchResponseHit(h.documentId(), h.chunkIndex(), h.chunkText(), h.distance());
        }
    }
}
