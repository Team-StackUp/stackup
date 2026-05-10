package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
	@NotEmpty List<String> allowedOrigins
) {
}
