package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.sse")
public record SseProperties(
	@NotNull Duration timeout,
	@NotNull Duration keepAliveInterval,
	@NotNull Duration streamTokenTtl
)
{
	public SseProperties {
		validatePositive("timeout", timeout);
		validatePositive("keepAliveInterval", keepAliveInterval);
		validatePositive("streamTokenTtl", streamTokenTtl);
	}

	private static void validatePositive(String name, Duration value) {
		if (value != null && !value.isPositive()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
