package com.stackup.stackup.session.presentation;

import com.stackup.stackup.session.application.InterviewMessageService;
import com.stackup.stackup.session.presentation.dto.InternalAnswerSubmitRequest;
import com.stackup.stackup.session.presentation.dto.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// RealTime 서버가 WS 로 받은 답변을 위임하는 내부 엔드포인트. X-Internal-API-Key 필요.
// userId 는 RealTime 이 Stream 토큰에서 검증한 값을 body 로 전달. 기존 submitAnswer 가
// 세션 소유권(userId+sessionId)을 다시 검증하므로 위변조 방어가 이중으로 유지된다.
@Tag(name = "Internal: Session Messages", description = "X-Internal-API-Key 필요. RealTime WS 답변 위임.")
@RestController
@RequestMapping("/api/internal/sessions/{sessionId}/messages")
@RequiredArgsConstructor
public class InternalSessionMessageController {

    private final InterviewMessageService messageService;

    @Operation(operationId = "internalSubmitAnswer", summary = "RealTime WS 답변 위임 → 답변 INSERT + 꼬리질문 발행")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "답변 저장"),
        @ApiResponse(responseCode = "401", description = "X-Internal-API-Key 인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음 / 소유자 불일치"),
        @ApiResponse(responseCode = "422", description = "세션 IN_PROGRESS 아님 또는 직전이 질문 아님")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse submit(
        @PathVariable Long sessionId,
        @Valid @RequestBody InternalAnswerSubmitRequest request
    ) {
        return MessageResponse.from(
            messageService.submitAnswer(request.userId(), sessionId, request.content(), request.idempotencyKey())
        );
    }
}
