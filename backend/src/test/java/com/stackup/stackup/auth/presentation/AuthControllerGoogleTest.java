package com.stackup.stackup.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stackup.stackup.auth.application.AuthService;
import com.stackup.stackup.auth.application.dto.AuthenticatedUserResult;
import com.stackup.stackup.auth.application.dto.OAuthCallbackResult;
import com.stackup.stackup.auth.application.dto.OAuthLoginResult;
import com.stackup.stackup.auth.presentation.dto.OAuthCallbackResponse;
import com.stackup.stackup.auth.presentation.dto.OAuthLoginResponse;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.user.domain.OAuthProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerGoogleTest {

    @Mock
    private AuthService authService;

    private final SecurityProperties securityProperties = new SecurityProperties(
        "jwt-secret", "encryption-key", 900L, 1209600L, "Bearer",
        Duration.ofMinutes(10), 32, 32, 32,
        "refresh_token", "/", "Lax", true, true, "internal-key"
    );

    @Test
    void startGoogleLogin_returnsAuthorizationUrl() {
        AuthController controller = new AuthController(authService, securityProperties);
        when(authService.startGoogleLogin())
            .thenReturn(new OAuthLoginResult("https://accounts.google.com/o/oauth2/v2/auth?x=1", "state-1"));

        ResponseEntity<OAuthLoginResponse> response = controller.startGoogleLogin();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().authorizationUrl()).startsWith("https://accounts.google.com");
        assertThat(response.getBody().state()).isEqualTo("state-1");
    }

    @Test
    void googleCallback_setsRefreshCookieAndReturnsGoogleUser() {
        AuthController controller = new AuthController(authService, securityProperties);
        AuthenticatedUserResult user = new AuthenticatedUserResult(
            7L, OAuthProvider.GOOGLE, "홍길동", null, null, "hong@example.com", "avatar"
        );
        when(authService.completeGoogleLogin("code-1", "state-1")).thenReturn(new OAuthCallbackResult(
            "access-token", "Bearer", 900L, user, true, "refresh-raw", 1209600L
        ));

        ResponseEntity<OAuthCallbackResponse> response = controller.googleCallback("code-1", "state-1");

        // GitHub 콜백과 동일하게 refresh 토큰은 httpOnly 쿠키로만 나간다 — 본문에 실리면 안 된다.
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refresh_token=refresh-raw").contains("HttpOnly");

        OAuthCallbackResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accessToken()).isEqualTo("access-token");
        assertThat(body.isNewUser()).isTrue();
        assertThat(body.user().provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(body.user().displayName()).isEqualTo("홍길동");
        // Google 계정에는 GitHub 식별자가 없다 — 프론트가 이 null 로 레포 기능을 가린다.
        assertThat(body.user().githubId()).isNull();
        assertThat(body.user().githubUsername()).isNull();
    }
}
