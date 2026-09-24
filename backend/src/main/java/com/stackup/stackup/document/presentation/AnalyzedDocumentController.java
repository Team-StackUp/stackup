package com.stackup.stackup.document.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.document.application.AnalysisRequestService.AnalysisHandle;
import com.stackup.stackup.document.application.AnalyzedDocumentQueryService;
import com.stackup.stackup.document.application.DocumentReanalysisService;
import com.stackup.stackup.document.presentation.dto.ReanalyzeDocumentResponse;
import com.stackup.stackup.document.presentation.dto.AnalyzedDocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Documents", description = "분석 문서(이력서/레포 공통) 조회. 상세 조회 시 분석 마크다운의 presigned S3 URL 포함.")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class AnalyzedDocumentController {

    private final AnalyzedDocumentQueryService queryService;
    private final DocumentReanalysisService reanalysisService;

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

    @Operation(
        operationId = "getAnalyzedDocumentContent",
        summary = "분석 원문(마크다운) 프록시",
        description = "presigned URL 은 내부(MinIO) 호스트라 브라우저가 직접 접근할 수 없어 "
            + "Core 가 원문 바이트를 중계한다 (TTS 오디오 프록시와 동일 패턴)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "분석 원문 (text/markdown)"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "분석 문서 없음"),
        @ApiResponse(responseCode = "422", description = "아직 분석 산출물이 없음")
    })
    @GetMapping("/{documentId}/content")
    public ResponseEntity<Resource> content(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long documentId
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown; charset=utf-8"))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
            .body(new InputStreamResource(queryService.getContentForUser(principal.userId(), documentId)));
    }

    @Operation(
        operationId = "reanalyzeDocument",
        summary = "분석에 실패한 문서 다시 분석",
        description = "원본(S3 파일·URL·자소서 본문)은 그대로 살아 있으므로 같은 자료로 분석을 다시 "
            + "요청한다. 자료를 지우고 다시 등록할 필요가 없다. 새 분석 문서가 만들어지고 "
            + "실패한 문서는 목록에서 사라진다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "재분석 요청 발행 — 새 문서 id 반환"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "문서 없음"),
        @ApiResponse(responseCode = "422", description = "실패 상태가 아니라 다시 분석할 대상이 아님")
    })
    @PostMapping("/{documentId}/reanalyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReanalyzeDocumentResponse reanalyze(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long documentId
    ) {
        AnalysisHandle handle = reanalysisService.reanalyze(principal.userId(), documentId);
        return new ReanalyzeDocumentResponse(handle.analyzedDocumentId());
    }
}
