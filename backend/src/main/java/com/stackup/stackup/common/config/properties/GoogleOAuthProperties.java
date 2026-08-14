package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Google OAuth 설정.
 *
 * clientId/clientSecret 에 @NotBlank 를 걸지 않는다 — 배포는 dev 머지 시 자동으로 도는데,
 * 시크릿이 아직 없는 상태에서 검증에 걸리면 **애플리케이션 전체가 부팅에 실패**한다.
 * Google 로그인만 못 쓰는 상태로 기동하고, 실제 호출 시점에 {@link #isConfigured()} 로 막는다.
 */
@Validated
@ConfigurationProperties(prefix = "app.google")
public record GoogleOAuthProperties(
	String clientId,
	String clientSecret,
	@NotNull URI redirectUri,
	@NotBlank String scopes,
	@NotNull URI authorizationBaseUrl,
	@NotNull URI tokenBaseUrl,
	@NotNull URI userInfoBaseUrl,
	@NotBlank String codeChallengeMethod,
	@NotNull Duration connectTimeout,
	@NotNull Duration readTimeout
) {

	public GoogleOAuthProperties {
		validatePositive("connectTimeout", connectTimeout);
		validatePositive("readTimeout", readTimeout);
	}

	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank()
				&& clientSecret != null && !clientSecret.isBlank()
				&& redirectUri != null && !redirectUri.toString().isBlank();
	}

	@Override
	public String toString() {
		return "GoogleOAuthProperties[clientId=" + clientId
				+ ", clientSecret=******"
				+ ", redirectUri=" + redirectUri
				+ ", scopes=" + scopes
				+ ", authorizationBaseUrl=" + authorizationBaseUrl
				+ ", tokenBaseUrl=" + tokenBaseUrl
				+ ", userInfoBaseUrl=" + userInfoBaseUrl
				+ ", codeChallengeMethod=" + codeChallengeMethod
				+ ", connectTimeout=" + connectTimeout
				+ ", readTimeout=" + readTimeout + "]";
	}

	private static void validatePositive(String name, Duration value) {
		if (value != null && !value.isPositive()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
