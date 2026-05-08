package com.chat2b.admissions.service.generation;

import com.chat2b.admissions.config.GenerationProperties;
import com.chat2b.admissions.model.GenerationRequest;
import com.chat2b.admissions.model.GenerationResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ChatModelRouter {

	private final GenerationProperties properties;
	private final List<ChatModel> models;
	private final MockChatModel mockChatModel;

	public ChatModelRouter(GenerationProperties properties, List<ChatModel> models, MockChatModel mockChatModel) {
		this.properties = properties;
		this.models = models;
		this.mockChatModel = mockChatModel;
	}

	public GenerationResult generate(GenerationRequest request) {
		ChatModel selected = selectConfiguredModel();
		if (selected.isConfigured()) {
			return selected.generate(request);
		}
		if (properties.isAllowMockFallback()) {
			return mockChatModel.generate(request);
		}
		throw new IllegalStateException("Configured generation provider is not available: " + selected.provider());
	}

	public String answerMode() {
		ChatModel selected = selectConfiguredModel();
		if (selected.isConfigured()) {
			return selected.provider();
		}
		return properties.isAllowMockFallback() ? "mock-fallback" : selected.provider() + "-unconfigured";
	}

	private ChatModel selectConfiguredModel() {
		String provider = properties.getProvider() == null
			? "gemini"
			: properties.getProvider().trim().toLowerCase(Locale.ROOT);
		return models.stream()
			.filter(model -> provider.equals(model.provider()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Unsupported generation provider: " + provider));
	}
}
