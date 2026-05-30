package com.stackup.stackup.document.presentation;

import com.stackup.stackup.document.application.DocumentEmbeddingSearchService;
import com.stackup.stackup.document.presentation.dto.InternalEmbeddingSearchRequest;
import com.stackup.stackup.document.presentation.dto.InternalEmbeddingSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal: Embedding Search", description = "X-Internal-API-Key 필요. AI 서버가 세션 컨텍스트 문서 청크를 pgvector 로 검색.")
@RestController
@RequestMapping("/api/internal/embeddings")
@RequiredArgsConstructor
public class InternalEmbeddingSearchController {

    private final DocumentEmbeddingSearchService searchService;

    @Operation(
            operationId = "internalSearchEmbeddings",
            summary = "세션 컨텍스트 문서 임베딩 검색",
            description = "AI 서버가 생성한 queryEmbedding 으로 세션에 연결된 분석 완료 문서의 상위 chunk 를 검색합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공. 결과가 없으면 빈 results 배열 반환"),
            @ApiResponse(responseCode = "400", description = "요청 payload 검증 실패"),
            @ApiResponse(responseCode = "401", description = "X-Internal-API-Key 인증 실패"),
            @ApiResponse(responseCode = "403", description = "요청 문서가 세션 컨텍스트에 없거나 분석 완료 상태가 아님"),
            @ApiResponse(responseCode = "404", description = "sessionId 에 해당하는 세션 없음")
    })
    @PostMapping("/search")
    public InternalEmbeddingSearchResponse search(@Valid @RequestBody InternalEmbeddingSearchRequest request) {
        return InternalEmbeddingSearchResponse.from(searchService.search(request.toCommand()));
    }
}
