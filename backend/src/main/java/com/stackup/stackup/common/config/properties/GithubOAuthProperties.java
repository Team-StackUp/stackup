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
	@NotBlank String scopes
) {

	@Override
	public String toString() {
		return "GithubOAuthProperties[clientId=" + clientId
				+ ", clientSecret=******"
				+ ", redirectUri=" + redirectUri
				+ ", scopes=" + scopes + "]";
	}
}
