package com.chat2b.admissions.service;

import com.chat2b.admissions.config.GeminiProperties;
import com.chat2b.admissions.config.GenerationProperties;
import com.chat2b.admissions.model.GenerationMetadata;
import com.chat2b.admissions.model.GenerationRequest;
import com.chat2b.admissions.model.GenerationResult;
import com.chat2b.admissions.model.RetrievedChunk;
import com.chat2b.admissions.service.generation.GenerationCostEstimator;
import com.chat2b.admissions.service.generation.PromptTemplates;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class GeminiGateway {

	private final GeminiProperties properties;
	private final GenerationProperties generationProperties;
	private final GenerationCostEstimator costEstimator;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public GeminiGateway(
		GeminiProperties properties,
		GenerationProperties generationProperties,
		GenerationCostEstimator costEstimator
	) {
		this.properties = properties;
		this.generationProperties = generationProperties;
		this.costEstimator = costEstimator;
		this.objectMapper = new ObjectMapper()
			.configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), false);
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();
	}

	public boolean isConfigured() {
		return properties.isConfigured();
	}

	public String embeddingModelName() {
		return properties.getEmbeddingModel();
	}

	public float[] createQueryEmbedding(String text) {
		return createEmbedding(text, "RETRIEVAL_QUERY", null);
	}

	public float[] createDocumentEmbedding(String text, String title) {
		return createEmbedding(text, "RETRIEVAL_DOCUMENT", title);
	}

	public String generateAnswer(String question, List<RetrievedChunk> retrievedChunks) {
		return generateAnswer(new GenerationRequest(question, retrievedChunks, false)).answer();
	}

	public GenerationResult generateAnswer(GenerationRequest request) {
		ObjectNode payload = objectMapper.createObjectNode();
		ObjectNode systemInstruction = payload.putObject("systemInstruction");
		ArrayNode systemParts = systemInstruction.putArray("parts");
		systemParts.addObject().put("text", PromptTemplates.GROUNDED_QA_SYSTEM_PROMPT);

		ArrayNode contents = payload.putArray("contents");
		ObjectNode userContent = contents.addObject();
		userContent.put("role", "user");
		ArrayNode userParts = userContent.putArray("parts");
		userParts.addObject().put("text", PromptTemplates.buildUserPrompt(request.question(), request.retrievedChunks()));

		ObjectNode generationConfig = payload.putObject("generationConfig");
		generationConfig.put("temperature", generationProperties.getTemperature());
		generationConfig.put("maxOutputTokens", generationProperties.getMaxOutputTokens());
		generationConfig.put("responseMimeType", "text/plain");

		String model = generationProperties.selectModel(request.important());
		JsonNode root = post("/models/%s:generateContent".formatted(modelCode(model)), payload);
		JsonNode candidates = root.path("candidates");
		if (!candidates.isArray() || candidates.isEmpty()) {
			throw new IllegalStateException("Gemini response did not include any candidates.");
		}

		StringBuilder content = new StringBuilder();
		for (JsonNode part : candidates.path(0).path("content").path("parts")) {
			JsonNode textNode = part.path("text");
			if (textNode.isTextual()) {
				content.append(textNode.asText());
			}
		}
		if (!content.isEmpty()) {
			return new GenerationResult(content.toString().trim(), metadata(root, model, request.important()));
		}
		throw new IllegalStateException("Gemini response did not include text content.");
	}

	private float[] createEmbedding(String text, String taskType, String title) {
		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("model", modelResourceName(properties.getEmbeddingModel()));
		payload.put("taskType", taskType);
		payload.put("outputDimensionality", properties.getEmbeddingDimensions());
		if (StringUtils.hasText(title) && "RETRIEVAL_DOCUMENT".equals(taskType)) {
			payload.put("title", title);
		}

		ObjectNode content = payload.putObject("content");
		ArrayNode parts = content.putArray("parts");
		parts.addObject().put("text", text);

		JsonNode root = post("/models/%s:embedContent".formatted(modelCode(properties.getEmbeddingModel())), payload);
		ArrayNode embedding = (ArrayNode) root.path("embedding").path("values");
		if (embedding == null || embedding.isEmpty()) {
			throw new IllegalStateException("Gemini embeddings response did not include an embedding.");
		}
		float[] result = new float[embedding.size()];
		for (int index = 0; index < embedding.size(); index++) {
			result[index] = (float) embedding.get(index).asDouble();
		}
		return result;
	}

	private GenerationMetadata metadata(JsonNode root, String requestedModel, boolean important) {
		JsonNode usage = root.path("usageMetadata");
		Integer inputTokens = intOrNull(usage, "promptTokenCount");
		Integer outputTokens = intOrNull(usage, "candidatesTokenCount");
		Integer totalTokens = intOrNull(usage, "totalTokenCount");
		return new GenerationMetadata(
			"gemini",
			requestedModel,
			root.path("modelVersion").isTextual() ? root.path("modelVersion").asText() : requestedModel,
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

	private JsonNode post(String path, JsonNode payload) {
		if (!isConfigured()) {
			throw new IllegalStateException("Gemini API key is not configured.");
		}
		try {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(properties.getBaseUrl() + path))
				.header("x-goog-api-key", properties.getApiKey())
				.header("Content-Type", "application/json; charset=UTF-8")
				.timeout(Duration.ofSeconds(45))
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
				.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("Gemini API request failed: " + response.body());
			}
			return objectMapper.readTree(response.body());
		} catch (IOException exception) {
			throw new IllegalStateException("Gemini API request failed.", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Gemini API request was interrupted.", exception);
		}
	}

	private Integer intOrNull(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		return value.isInt() ? value.asInt() : null;
	}

	private String modelCode(String modelName) {
		return modelName.startsWith("models/")
			? modelName.substring("models/".length())
			: modelName;
	}

	private String modelResourceName(String modelName) {
		return modelName.startsWith("models/")
			? modelName
			: "models/" + modelName;
	}
}
