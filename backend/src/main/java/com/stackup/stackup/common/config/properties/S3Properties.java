package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
	@NotNull
	URI endpoint,
	@NotBlank String accessKey,
	@NotBlank String secretKey,
	@NotBlank String bucket,
	@NotBlank String region,
	boolean pathStyle
) {
}
