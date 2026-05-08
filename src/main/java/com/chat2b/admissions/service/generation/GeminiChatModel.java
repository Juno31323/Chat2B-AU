package com.chat2b.admissions.service.generation;

import com.chat2b.admissions.model.GenerationRequest;
import com.chat2b.admissions.model.GenerationResult;
import com.chat2b.admissions.service.GeminiGateway;
import org.springframework.stereotype.Component;

@Component
public class GeminiChatModel implements ChatModel {

	private final GeminiGateway geminiGateway;

	public GeminiChatModel(GeminiGateway geminiGateway) {
		this.geminiGateway = geminiGateway;
	}

	@Override
	public String provider() {
		return "gemini";
	}

	@Override
	public boolean isConfigured() {
		return geminiGateway.isConfigured();
	}

	@Override
	public GenerationResult generate(GenerationRequest request) {
		return geminiGateway.generateAnswer(request);
	}
}
