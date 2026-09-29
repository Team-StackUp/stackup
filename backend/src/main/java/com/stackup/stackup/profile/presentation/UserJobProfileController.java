package com.stackup.stackup.profile.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.profile.application.UserJobProfileService;
import com.stackup.stackup.profile.application.dto.JobProfileResponse;
import com.stackup.stackup.profile.application.dto.JobProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users (Job Profile)", description = "희망 직군·산업·경력 — 면접 생성 시 기본값으로 쓰인다.")
@RestController
@RequiredArgsConstructor
public class UserJobProfileController {

    private final UserJobProfileService service;

    @Operation(
        operationId = "getMyJobProfile",
        summary = "내 취업 프로필 조회",
        description = "프로필을 채운 적 없으면 404 가 아니라 빈 프로필(filled=false)을 반환한다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "프로필 반환"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/api/users/me/job-profile")
    public JobProfileResponse get(@AuthenticationPrincipal UserPrincipal principal) {
        return service.get(principal.userId());
    }

    @Operation(
        operationId = "updateMyJobProfile",
        summary = "내 취업 프로필 저장",
        description = "생략한(null) 항목은 그대로 둔다. 비우려면 빈 목록 / 빈 문자열을 보낸다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장 후 최신 프로필 반환"),
        @ApiResponse(responseCode = "400", description = "검증 실패"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PutMapping("/api/users/me/job-profile")
    public JobProfileResponse update(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody JobProfileUpdateRequest request
    ) {
        return service.update(principal.userId(), request);
    }
}
