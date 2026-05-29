package com.stackup.stackup.user.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.user.application.UserConsentService;
import com.stackup.stackup.user.domain.consent.ConsentType;
import com.stackup.stackup.user.presentation.dto.ConsentResponse;
import com.stackup.stackup.user.presentation.dto.ConsentSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Consents", description = "개인정보처리/이용약관/마케팅 동의 제출·이력·철회 (append-only audit).")
@RestController
@RequestMapping("/api/users/me/consents")
@RequiredArgsConstructor
public class UserConsentController {

    private final UserConsentService consentService;

    @Operation(operationId = "submitConsent", summary = "동의 제출 (새 row 생성)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "동의 row 생성"),
        @ApiResponse(responseCode = "400", description = "요청 검증 실패"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse submit(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody ConsentSubmitRequest request,
        HttpServletRequest http
    ) {
        return ConsentResponse.from(
            consentService.submit(principal.userId(), request.toCommand(clientIp(http)))
        );
    }

    @Operation(operationId = "listConsents", summary = "내 동의 이력 (최신순)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이력 목록"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public List<ConsentResponse> history(@AuthenticationPrincipal UserPrincipal principal) {
        return consentService.history(principal.userId()).stream()
            .map(ConsentResponse::from)
            .toList();
    }

    @Operation(operationId = "revokeConsent", summary = "동의 철회 (최신 활성 row 의 revoked_at 갱신)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "철회 처리됨"),
        @ApiResponse(responseCode = "400", description = "철회할 활성 동의 없음"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @DeleteMapping("/{type}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable ConsentType type
    ) {
        consentService.revoke(principal.userId(), type);
    }

    private String clientIp(HttpServletRequest http) {
        // X-Forwarded-For (Nginx 등 프록시 뒤) 우선, 없으면 remote addr
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return http.getRemoteAddr();
    }
}
