package com.stackup.stackup.auth.presentation;

import com.stackup.stackup.auth.application.AuthService;
import com.stackup.stackup.auth.application.dto.OAuthCallbackResult;
import com.stackup.stackup.auth.application.dto.OAuthLoginResult;
import com.stackup.stackup.auth.application.dto.RefreshTokenResult;
import com.stackup.stackup.auth.application.dto.StreamTokenResult;
import com.stackup.stackup.auth.presentation.dto.AuthUserResponse;
import com.stackup.stackup.auth.presentation.dto.OAuthCallbackResponse;
import com.stackup.stackup.auth.presentation.dto.OAuthLoginResponse;
import com.stackup.stackup.auth.presentation.dto.RefreshTokenResponse;
import com.stackup.stackup.auth.presentation.dto.StreamTokenResponse;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "OAuth login (GitHub, Google) and authentication token APIs")
public record AuthController(AuthService authService, SecurityProperties securityProperties) {

    @Operation(operationId = "startGithubLogin", summary = "Create GitHub OAuth authorization URL")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "GitHub authorization URL created")
    })
    @PostMapping("/github")
    public ResponseEntity<OAuthLoginResponse> startGithubLogin() {
        OAuthLoginResult result = authService.startGithubLogin();
        return ResponseEntity.ok(new OAuthLoginResponse(result.authorizationUrl(), result.state()));
    }

    @Operation(operationId = "completeGithubLogin", summary = "Complete GitHub OAuth login")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login completed"),
        @ApiResponse(responseCode = "401", description = "GitHub OAuth login failed")
    })
    @GetMapping("/github/callback")
    public ResponseEntity<OAuthCallbackResponse> githubCallback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state
    ) {
        return loginResponse(authService.completeGithubLogin(code, state));
    }

    @Operation(operationId = "startGoogleLogin", summary = "Create Google OAuth authorization URL")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Google authorization URL created")
    })
    @PostMapping("/google")
    public ResponseEntity<OAuthLoginResponse> startGoogleLogin() {
        OAuthLoginResult result = authService.startGoogleLogin();
        return ResponseEntity.ok(new OAuthLoginResponse(result.authorizationUrl(), result.state()));
    }

    @Operation(operationId = "completeGoogleLogin", summary = "Complete Google OAuth login")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login completed"),
        @ApiResponse(responseCode = "401", description = "Google OAuth login failed")
    })
    @GetMapping("/google/callback")
    public ResponseEntity<OAuthCallbackResponse> googleCallback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state
    ) {
        return loginResponse(authService.completeGoogleLogin(code, state));
    }

    private ResponseEntity<OAuthCallbackResponse> loginResponse(OAuthCallbackResult result) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(
                result.refreshTokenRawForCookie(),
                result.refreshTokenMaxAgeSeconds()
            ).toString())
            .body(new OAuthCallbackResponse(
                result.accessToken(),
                result.tokenType(),
                result.expiresIn(),
                AuthUserResponse.from(result.user()),
                result.isNewUser()
            ));
    }

    @Operation(operationId = "refreshAccessToken", summary = "Refresh StackUp access token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Access token refreshed"),
        @ApiResponse(responseCode = "401", description = "Refresh token is missing or invalid")
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(
        @CookieValue(name = "${app.security.refresh-token-cookie-name}", required = false) String refreshToken
    ) {
        RefreshTokenResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(
                result.refreshTokenRawForCookie(),
                result.refreshTokenMaxAgeSeconds()
            ).toString())
            .body(new RefreshTokenResponse(
                result.accessToken(),
                result.tokenType(),
                result.expiresIn()
            ));
    }

    @Operation(operationId = "logout", summary = "Logout and revoke refresh token")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out")
    })
    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = "${app.security.refresh-token-cookie-name}", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie().toString())
            .build();
    }

    @Operation(operationId = "createStreamToken", summary = "Create SSE stream token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stream token created"),
        @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    @PostMapping("/stream-token")
    public ResponseEntity<StreamTokenResponse> createStreamToken(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal == null ? null : principal.userId();
        StreamTokenResult result = authService.createStreamToken(userId);
        return ResponseEntity.ok(new StreamTokenResponse(result.streamToken()));
    }

    private ResponseCookie refreshTokenCookie(String refreshToken, long maxAgeSeconds) {
        return ResponseCookie.from(securityProperties.refreshTokenCookieName(), refreshToken)
            .httpOnly(securityProperties.refreshTokenCookieHttpOnly())
            .secure(securityProperties.refreshTokenCookieSecure())
            .sameSite(securityProperties.refreshTokenCookieSameSite())
            .path(securityProperties.refreshTokenCookiePath())
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .build();
    }

    private ResponseCookie expiredRefreshTokenCookie() {
        return ResponseCookie.from(securityProperties.refreshTokenCookieName(), "")
            .httpOnly(securityProperties.refreshTokenCookieHttpOnly())
            .secure(securityProperties.refreshTokenCookieSecure())
            .sameSite(securityProperties.refreshTokenCookieSameSite())
            .path(securityProperties.refreshTokenCookiePath())
            .maxAge(Duration.ZERO)
            .build();
    }
}
