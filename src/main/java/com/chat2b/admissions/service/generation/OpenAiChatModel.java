package com.chat2b.admissions.service.generation;

import com.chat2b.admissions.config.GenerationProperties;
import com.chat2b.admissions.config.OpenAiProperties;
import com.chat2b.admissions.model.GenerationMetadata;
import com.chat2b.admissions.model.GenerationRequest;
import com.chat2b.admissions.model.GenerationResult;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Component
public class OpenAiChatModel implements ChatModel {

	private final OpenAiProperties openAiProperties;
	private final GenerationProperties generationProperties;
	private final GenerationCostEstimator costEstimator;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public OpenAiChatModel(
		OpenAiProperties openAiProperties,
		GenerationProperties generationProperties,
		GenerationCostEstimator costEstimator
	) {
		this.openAiProperties = openAiProperties;
		this.generationProperties = generationProperties;
		this.costEstimator = costEstimator;
		this.objectMapper = new ObjectMapper()
			.configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), false);
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();
	}

	@Override
	public String provider() {
		return "openai";
	}

	@Override
	public boolean isConfigured() {
		return openAiProperties.isConfigured();
	}

	@Override
	public GenerationResult generate(GenerationRequest request) {
		if (!isConfigured()) {
			throw new IllegalStateException("OpenAI API key is not configured.");
		}
		String model = generationProperties.selectModel(request.important());
		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("model", model);
		payload.put("instructions", PromptTemplates.GROUNDED_QA_SYSTEM_PROMPT);
		payload.put("input", PromptTemplates.buildUserPrompt(request.question(), request.retrievedChunks()));
		payload.put("temperature", generationProperties.getTemperature());
		payload.put("max_output_tokens", generationProperties.getMaxOutputTokens());

		try {
			HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(openAiProperties.getBaseUrl() + "/responses"))
				.header("Authorization", "Bearer " + openAiProperties.getApiKey())
				.header("Content-Type", "application/json; charset=UTF-8")
				.timeout(Duration.ofSeconds(60))
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
				.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("OpenAI API request failed: " + response.body());
			}
			JsonNode root = objectMapper.readTree(response.body());
			return new GenerationResult(extractText(root), metadata(root, model, request.important()));
		} catch (IOException exception) {
			throw new IllegalStateException("OpenAI API request failed.", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("OpenAI API request was interrupted.", exception);
		}
	}

	private String extractText(JsonNode root) {
		JsonNode outputText = root.path("output_text");
		if (outputText.isTextual() && !outputText.asText().isBlank()) {
			return outputText.asText().trim();
		}
		for (JsonNode output : root.path("output")) {
			for (JsonNode content : output.path("content")) {
				JsonNode text = content.path("text");
				if (text.isTextual() && !text.asText().isBlank()) {
					return text.asText().trim();
				}
			}
		}
		throw new IllegalStateException("OpenAI response did not include text content.");
	}

	private GenerationMetadata metadata(JsonNode root, String requestedModel, boolean important) {
		JsonNode usage = root.path("usage");
		Integer inputTokens = intOrNull(usage, "input_tokens");
		Integer outputTokens = intOrNull(usage, "output_tokens");
		Integer totalTokens = intOrNull(usage, "total_tokens");
		return new GenerationMetadata(
			"openai",
			requestedModel,
			root.path("model").isTextual() ? root.path("model").asText() : requestedModel,
			generationProperties.getTemperature(),
			generationProperties.getMaxOutputTokens(),
			generationProperties.getPromptVersion(),
			Instant.now(),
			inputTokens,
			outputTokens,
			totalTokens,
			costEstimator.estimateUsd(inputTokens, outputTokens, important)
		);
	}

	private Integer intOrNull(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		return value.isInt() ? value.asInt() : null;
	}
}
