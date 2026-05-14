package com.stackup.stackup.auth.presentation;

import com.stackup.stackup.auth.application.AuthService;
import com.stackup.stackup.auth.application.dto.GithubCallbackResult;
import com.stackup.stackup.auth.application.dto.GithubLoginResult;
import com.stackup.stackup.auth.application.dto.RefreshTokenResult;
import com.stackup.stackup.auth.application.dto.StreamTokenResult;
import com.stackup.stackup.auth.presentation.dto.AuthUserResponse;
import com.stackup.stackup.auth.presentation.dto.GithubCallbackResponse;
import com.stackup.stackup.auth.presentation.dto.GithubLoginResponse;
import com.stackup.stackup.auth.presentation.dto.RefreshTokenResponse;
import com.stackup.stackup.auth.presentation.dto.StreamTokenResponse;
import com.stackup.stackup.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Auth", description = "GitHub OAuth login and authentication token APIs")
public record AuthController(AuthService authService) {

    @Operation(operationId = "startGithubLogin", summary = "Create GitHub OAuth authorization URL")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "GitHub authorization URL created")
    })
    @PostMapping("/github")
    public ResponseEntity<GithubLoginResponse> startGithubLogin() {
        GithubLoginResult result = authService.startGithubLogin();
        return ResponseEntity.ok(new GithubLoginResponse(result.authorizationUrl(), result.state()));
    }

    @Operation(operationId = "completeGithubLogin", summary = "Complete GitHub OAuth login")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login completed"),
        @ApiResponse(responseCode = "401", description = "GitHub OAuth login failed")
    })
    @GetMapping("/github/callback")
    public ResponseEntity<GithubCallbackResponse> githubCallback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state
    ) {
        GithubCallbackResult result = authService.completeGithubLogin(code, state);
        // TODO: Set refresh_token HttpOnly cookie when refresh token persistence is implemented.
        return ResponseEntity.ok(new GithubCallbackResponse(
            result.accessToken(),
            result.tokenType(),
            result.expiresIn(),
            new AuthUserResponse(
                result.user().id(),
                result.user().githubId(),
                result.user().githubUsername(),
                result.user().email(),
                result.user().avatarUrl()
            ),
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
        @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        RefreshTokenResult result = authService.refresh(refreshToken);
        // TODO: Rotate refresh_token HttpOnly cookie when refresh token persistence is implemented.
        return ResponseEntity.ok(new RefreshTokenResponse(
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
        @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        // TODO: Expire refresh_token HttpOnly cookie when refresh token persistence is implemented.
        return ResponseEntity.noContent().build();
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
}
