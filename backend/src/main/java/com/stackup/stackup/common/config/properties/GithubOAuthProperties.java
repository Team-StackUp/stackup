package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.github")
public record GithubOAuthProperties(
	@NotBlank String clientId,
	@NotBlank String clientSecret,
	@NotNull
	URI redirectUri,
	@NotBlank String scopes,
	@NotNull URI oauthBaseUrl,
	@NotBlank String tokenType,
	@NotBlank String codeChallengeMethod,
	@NotBlank String apiBaseUrl,
	@NotBlank String apiVersion,
	@NotNull Duration connectTimeout,
	@NotNull Duration readTimeout
) {

	public GithubOAuthProperties {
		validatePositive("connectTimeout", connectTimeout);
		validatePositive("readTimeout", readTimeout);
	}

	@Override
	public String toString() {
		return "GithubOAuthProperties[clientId=" + clientId
				+ ", clientSecret=******"
				+ ", redirectUri=" + redirectUri
				+ ", scopes=" + scopes
				+ ", oauthBaseUrl=" + oauthBaseUrl
				+ ", tokenType=" + tokenType
				+ ", codeChallengeMethod=" + codeChallengeMethod
				+ ", apiBaseUrl=" + apiBaseUrl
				+ ", apiVersion=" + apiVersion
				+ ", connectTimeout=" + connectTimeout
				+ ", readTimeout=" + readTimeout + "]";
	}

	private static void validatePositive(String name, Duration value) {
		if (value != null && !value.isPositive()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
