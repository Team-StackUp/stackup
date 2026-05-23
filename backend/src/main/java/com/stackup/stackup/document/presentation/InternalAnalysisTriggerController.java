package com.stackup.stackup.document.presentation;

import com.stackup.stackup.document.application.AnalysisRequestService;
import com.stackup.stackup.document.application.AnalysisRequestService.AnalysisHandle;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 운영환경에서는 AnalysisRequestService 직접 호출 
@RestController
@RequestMapping("/api/internal/analyses")
@RequiredArgsConstructor
public class InternalAnalysisTriggerController {

    private final AnalysisRequestService service;

    @PostMapping("/resume/{resumeId}")
    public AnalysisHandle triggerResume(
        @PathVariable Long resumeId,
        @RequestBody TriggerRequest request
    ) {
        return service.requestResumeAnalysis(request.userId(), resumeId);
    }

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
