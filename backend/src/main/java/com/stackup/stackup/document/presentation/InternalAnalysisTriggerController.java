package com.stackup.stackup.document.presentation;

import com.stackup.stackup.document.application.AnalysisRequestService;
import com.stackup.stackup.document.application.AnalysisRequestService.AnalysisHandle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal: Analysis Triggers", description = "X-Internal-API-Key 필요. e2e 검증/디버그용 강제 트리거. 실제 사용자 흐름은 POST /api/resumes, POST /api/repositories 가 자동 트리거.")
@RestController
@RequestMapping("/api/internal/analyses")
@RequiredArgsConstructor
public class InternalAnalysisTriggerController {

    private final AnalysisRequestService service;

    @Operation(operationId = "internalTriggerResumeAnalysis", summary = "이력서 분석 강제 트리거")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "트리거 성공"),
        @ApiResponse(responseCode = "401", description = "X-Internal-API-Key 인증 실패")
    })
    @PostMapping("/resume/{resumeId}")
    public AnalysisHandle triggerResume(
        @PathVariable Long resumeId,
        @RequestBody TriggerRequest request
    ) {
        return service.requestResumeAnalysis(request.userId(), resumeId);
    }

    @Operation(operationId = "internalTriggerRepositoryAnalysis", summary = "GitHub 레포 분석 강제 트리거")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "트리거 성공"),
        @ApiResponse(responseCode = "401", description = "X-Internal-API-Key 인증 실패")
    })
    @PostMapping("/repository/{repositoryId}")
    public AnalysisHandle triggerRepository(
        @PathVariable Long repositoryId,
        @RequestBody TriggerRequest request
    ) {
        return service.requestRepositoryAnalysis(request.userId(), repositoryId);
    }

    public record TriggerRequest(@NotNull Long userId) {
    }
}
