package com.stackup.stackup.document.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "재분석 요청 결과 — 새로 만들어진 분석 문서 id")
public record ReanalyzeDocumentResponse(
    @Schema(description = "새 분석 문서 id (PROCESSING 상태로 시작)") Long documentId
) {
}
