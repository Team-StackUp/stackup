package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.resume")
public record ResumeProperties(
	@NotNull DataSize maxUploadSize
) {
}
