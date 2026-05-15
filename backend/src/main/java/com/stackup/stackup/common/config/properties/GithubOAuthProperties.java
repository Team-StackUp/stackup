package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
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
	@NotNull URI authorizationUrl,
	@NotNull URI tokenUrl,
	@NotBlank String tokenType,
	@NotBlank String codeChallengeMethod,
	@NotBlank String apiBaseUrl,
	@NotBlank String apiVersion
) {

	@Override
	public String toString() {
		return "GithubOAuthProperties[clientId=" + clientId
				+ ", clientSecret=******"
				+ ", redirectUri=" + redirectUri
				+ ", scopes=" + scopes
				+ ", authorizationUrl=" + authorizationUrl
				+ ", tokenUrl=" + tokenUrl
				+ ", tokenType=" + tokenType
				+ ", codeChallengeMethod=" + codeChallengeMethod
				+ ", apiBaseUrl=" + apiBaseUrl
				+ ", apiVersion=" + apiVersion + "]";
	}
}
