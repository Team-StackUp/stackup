package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
	@NotBlank String jwtSecret,
	@NotBlank String encryptionKey,
	@Positive long accessTokenTtlSeconds,
	@Positive long refreshTokenTtlSeconds
) {
}
