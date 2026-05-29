package com.stackup.stackup.log.ai.presentation;

import com.stackup.stackup.log.ai.application.AiRequestLogService;
import com.stackup.stackup.log.ai.application.dto.AiRequestLogCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal: AI Request Logs", description = "X-Internal-API-Key 필요. AI 가 LLM 호출별 토큰/지연시간을 기록.")
@RestController
@RequestMapping("/api/internal/ai-logs")
@RequiredArgsConstructor
public class InternalAiLogController {

    private final AiRequestLogService service;

    @Operation(operationId = "internalRecordAiRequestLog", summary = "AI 요청 로그 기록")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "기록 큐잉 (fire-and-forget)"),
        @ApiResponse(responseCode = "401", description = "X-Internal-API-Key 인증 실패")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void record(@Valid @RequestBody Request request) {
        service.record(new AiRequestLogCommand(
            request.userId(),
            request.sessionId(),
            request.requestType(),
            request.modelName(),
            request.inputTokens(),
            request.outputTokens(),
            request.latencyMs(),
            request.status(),
            request.errorMessage()
        ));
    }

    public record Request(
        Long userId,
        Long sessionId,
        @NotBlank String requestType,
        String modelName,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        @NotNull String status,
        String errorMessage
    ) {
    }
}
