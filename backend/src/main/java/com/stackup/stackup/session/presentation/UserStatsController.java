package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.UserStatsService;
import com.stackup.stackup.session.presentation.dto.UserStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// URL 은 /api/users/me/stats 이지만 데이터 출처가 session 도메인이라 controller 는 session 슬라이스에 둔다.
// (user → session 직접 의존 회피)
@Tag(name = "Users (Stats)", description = "사용자 면접 통계 — 총/완료 세션 수, 평균 점수, 최근 점수 추이.")
@RestController
@RequiredArgsConstructor
public class UserStatsController {

    private final UserStatsService statsService;

    @Operation(
        operationId = "getCurrentUserStats",
        summary = "내 면접 통계 (US-02)",
        description = "신규 사용자(데이터 없음)는 카운트 0, 평균 null, recent 빈 배열."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "통계 반환"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/api/users/me/stats")
    public UserStatsResponse getStats(@AuthenticationPrincipal UserPrincipal principal) {
        return UserStatsResponse.from(statsService.forUser(principal.userId()));
    }
}
