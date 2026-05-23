package com.stackup.stackup.document.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.document.application.AnalyzedDocumentQueryService;
import com.stackup.stackup.document.presentation.dto.AnalyzedDocumentResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class AnalyzedDocumentController {

    private final AnalyzedDocumentQueryService queryService;

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

    @GetMapping("/{documentId}")
    public AnalyzedDocumentResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long documentId
    ) {
        return AnalyzedDocumentResponse.from(queryService.getForUser(principal.userId(), documentId));
    }
}
