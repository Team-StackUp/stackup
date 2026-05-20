package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
	@NotBlank String jwtSecret,
	@NotBlank String encryptionKey,
	@Positive long accessTokenTtlSeconds,
	@Positive long refreshTokenTtlSeconds,
	@NotBlank String accessTokenType,
	@NotNull Duration oauthStateTtl,
	@Positive int oauthStateByteLength,
	@Positive int pkceCodeVerifierByteLength,
	@Positive int refreshTokenByteLength,
	@NotBlank String refreshTokenCookieName,
	@NotBlank String refreshTokenCookiePath,
	@NotBlank String refreshTokenCookieSameSite,
	boolean refreshTokenCookieSecure,
	boolean refreshTokenCookieHttpOnly,
	String internalApiKey
) {
}
