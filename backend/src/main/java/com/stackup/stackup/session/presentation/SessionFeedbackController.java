package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.SessionFeedbackQueryService;
import com.stackup.stackup.session.presentation.dto.FeedbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Session Feedback", description = "세션 종합 피드백 (US-24)")
@RestController
@RequestMapping("/api/sessions/{sessionId}/feedback")
@RequiredArgsConstructor
public class SessionFeedbackController {

    private final SessionFeedbackQueryService queryService;

    @Operation(operationId = "getSessionFeedback", summary = "세션 종합 피드백 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "피드백"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 또는 피드백 없음")
    })
    @GetMapping
    public FeedbackResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return FeedbackResponse.from(queryService.get(principal.userId(), sessionId));
    }

    @Operation(operationId = "shareSessionFeedback", summary = "피드백 공유 토큰 발급(멱등)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "공유 토큰"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 또는 피드백 없음")
    })
    @PostMapping("/share")
    public ShareResponse share(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return new ShareResponse(queryService.enableShare(principal.userId(), sessionId));
    }

    public record ShareResponse(String shareToken) {
    }
}
