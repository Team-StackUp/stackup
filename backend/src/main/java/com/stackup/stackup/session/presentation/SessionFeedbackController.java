package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.SessionFeedbackQueryService;
import com.stackup.stackup.session.presentation.dto.FeedbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @Operation(
        operationId = "getSessionFeedbackReport",
        summary = "AI 학습 리포트(마크다운) 프록시",
        description = "AI 가 피드백 생성 시 저장한 마크다운 리포트를 중계한다. "
            + "presigned URL 은 내부(MinIO) 호스트라 브라우저가 직접 접근할 수 없다 "
            + "(분석 원문 /content 프록시와 동일 패턴). 소유자 전용 — 공유(비인증) 응답에는 키 자체를 싣지 않는다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "리포트 (text/markdown)"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 또는 피드백 없음"),
        @ApiResponse(responseCode = "422", description = "리포트 파일 없음 (저장 실패 폴백·구버전 피드백)")
    })
    @GetMapping("/report")
    public ResponseEntity<Resource> report(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown; charset=utf-8"))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
            .body(new InputStreamResource(queryService.getReportContent(principal.userId(), sessionId)));
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

    @Operation(operationId = "unshareSessionFeedback", summary = "피드백 공유 해제(기존 링크 즉시 무효화, 멱등)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "공유 해제됨"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 또는 피드백 없음")
    })
    @DeleteMapping("/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshare(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        queryService.disableShare(principal.userId(), sessionId);
    }

    @Operation(operationId = "regenerateSessionFeedback",
        summary = "피드백 재생성 요청 — 발행 유실·AI 실패로 피드백이 오지 않을 때의 복구 경로")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "재생성 요청 접수"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음"),
        @ApiResponse(responseCode = "409", description = "피드백이 이미 존재"),
        @ApiResponse(responseCode = "422", description = "COMPLETED 세션이 아님")
    })
    @PostMapping("/regenerate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void regenerate(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        queryService.regenerate(principal.userId(), sessionId);
    }

    public record ShareResponse(String shareToken) {
    }
}
