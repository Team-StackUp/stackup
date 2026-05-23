package com.stackup.stackup.document.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.document.application.AnalyzedDocumentQueryService;
import com.stackup.stackup.document.presentation.dto.AnalyzedDocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Documents", description = "분석 문서(이력서/레포 공통) 조회. 상세 조회 시 분석 마크다운의 presigned S3 URL 포함.")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class AnalyzedDocumentController {

    private final AnalyzedDocumentQueryService queryService;

    @Operation(
        operationId = "listAnalyzedDocuments",
        summary = "내 분석 문서 목록 (filter: resumeId 또는 repositoryId)",
        description = "filter 미지정 시 사용자 보유 전체. resumeId/repositoryId 둘 다 지정 시 resumeId 가 우선."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "분석 문서 목록 (documentDownloadUrl 은 비어있음)"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public List<AnalyzedDocumentResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) Long resumeId,
        @RequestParam(required = false) Long repositoryId
    ) {
        return queryService.listForUser(principal.userId(), resumeId, repositoryId).stream()
            .map(AnalyzedDocumentResponse::from)
            .toList();
    }

    @Operation(
        operationId = "getAnalyzedDocument",
        summary = "분석 문서 상세 + presigned MD 다운로드 URL",
        description = "documentDownloadUrl 은 10분 TTL presigned. analysis_status 가 ANALYZED 인 경우에만 의미 있음."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "분석 문서 상세"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "분석 문서 없음")
    })
    @GetMapping("/{documentId}")
    public AnalyzedDocumentResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long documentId
    ) {
        return AnalyzedDocumentResponse.from(queryService.getForUser(principal.userId(), documentId));
    }
}
