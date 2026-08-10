package com.stackup.stackup.session.presentation;

import com.stackup.stackup.session.application.SessionFeedbackQueryService;
import com.stackup.stackup.session.presentation.dto.FeedbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 공유 토큰으로 피드백을 조회하는 공개(비인증) 엔드포인트. /api/public/** 는 permitAll.
@Tag(name = "Public: Shared Feedback", description = "공유 링크로 피드백 조회(비인증)")
@RestController
@RequestMapping("/api/public/feedbacks")
@RequiredArgsConstructor
public class PublicFeedbackController {

    private final SessionFeedbackQueryService queryService;

    @Operation(operationId = "getSharedFeedback", summary = "공유 토큰으로 피드백 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "피드백"),
        @ApiResponse(responseCode = "404", description = "공유된 피드백 없음")
    })
    @GetMapping("/{shareToken}")
    public FeedbackResponse get(@PathVariable String shareToken) {
        return FeedbackResponse.fromPublic(queryService.getByToken(shareToken));
    }
}
