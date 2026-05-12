package com.stackup.stackup.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.messaging.rabbitmq")
public record RabbitMqProperties(
	@NotBlank String publisher,
	@NotBlank String version,
	@NotNull
	Message message,
	@NotNull
	Template template,
	@NotNull
	Exchanges exchanges,
	@NotNull
	Queues queues,
	@NotNull
	RoutingKeyProperties routingKeys
) {

	public record Message(
		@NotBlank String contentType,
		@NotBlank String contentEncoding,
		@NotBlank String traceIdHeader
	) {
	}

	public record Template(
		boolean mandatory
	) {
	}

	public record Exchanges(
		boolean durable,
		boolean autoDelete,
		@NotNull
		Names names
	) {

		public record Names(
			@NotBlank String coreToAi,
			@NotBlank String aiToCore
		) {
		}
	}

	public record Queues(
		boolean durable,
		@NotNull
		Names names
	) {

		public record Names(
			@NotBlank String aiAnalyzeResume,
			@NotBlank String aiAnalyzeRepository,
			@NotBlank String aiGenerateQuestions,
			@NotBlank String aiGenerateFollowup,
			@NotBlank String coreCallbackAnalysis,
			@NotBlank String coreCallbackQuestions
		) {
		}
	}

	public record RoutingKeyProperties(
		@NotBlank String analyzeResume,
		@NotBlank String analyzeRepository,
		@NotBlank String generateQuestions,
		@NotBlank String generateFollowup,
		@NotBlank String callbackAnalysis,
		@NotBlank String callbackQuestions
	) {
	}
}
