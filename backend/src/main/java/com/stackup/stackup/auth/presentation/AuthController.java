package com.stackup.stackup.auth.presentation;

import com.stackup.stackup.auth.application.AuthService;
import com.stackup.stackup.auth.application.dto.GithubCallbackResult;
import com.stackup.stackup.auth.application.dto.GithubLoginResult;
import com.stackup.stackup.auth.application.dto.RefreshTokenResult;
import com.stackup.stackup.auth.application.dto.StreamTokenResult;
import com.stackup.stackup.auth.presentation.dto.GithubAuthRequest;
import com.stackup.stackup.auth.presentation.dto.GithubCallbackResponse;
import com.stackup.stackup.auth.presentation.dto.GithubLoginResponse;
import com.stackup.stackup.auth.presentation.dto.LogoutRequest;
import com.stackup.stackup.auth.presentation.dto.RefreshTokenRequest;
import com.stackup.stackup.auth.presentation.dto.RefreshTokenResponse;
import com.stackup.stackup.auth.presentation.dto.StreamTokenResponse;
import com.stackup.stackup.common.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/github")
    public ResponseEntity<GithubLoginResponse> startGithubLogin(@RequestBody(required = false) GithubAuthRequest request) {
        GithubLoginResult result = authService.startGithubLogin();
        return ResponseEntity.ok(new GithubLoginResponse(result.authorizationUrl(), result.state()));
    }

    @GetMapping("/github/callback")
    public ResponseEntity<GithubCallbackResponse> githubCallback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state
    ) {
        GithubCallbackResult result = authService.completeGithubLogin(code, state);
        return ResponseEntity.ok(new GithubCallbackResponse(
            result.accessToken(),
            result.refreshToken(),
            result.userId(),
            result.githubUsername()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request) {
        String refreshToken = request == null ? null : request.refreshToken();
        RefreshTokenResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok(new RefreshTokenResponse(result.accessToken(), result.refreshToken()));
    }

    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        String refreshToken = request == null ? null : request.refreshToken();
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stream-token")
    public ResponseEntity<StreamTokenResponse> createStreamToken(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal == null ? null : principal.userId();
        StreamTokenResult result = authService.createStreamToken(userId);
        return ResponseEntity.ok(new StreamTokenResponse(result.streamToken()));
    }
}
