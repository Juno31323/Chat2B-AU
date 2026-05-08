package com.chat2b.admissions.service.generation;

import com.chat2b.admissions.config.GenerationProperties;
import com.chat2b.admissions.model.GenerationMetadata;
import com.chat2b.admissions.model.GenerationRequest;
import com.chat2b.admissions.model.GenerationResult;
import com.chat2b.admissions.service.DemoAnswerComposer;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MockChatModel implements ChatModel {

	private final DemoAnswerComposer demoAnswerComposer;
	private final GenerationProperties properties;

	public MockChatModel(DemoAnswerComposer demoAnswerComposer, GenerationProperties properties) {
		this.demoAnswerComposer = demoAnswerComposer;
		this.properties = properties;
	}

	@Override
	public String provider() {
		return "mock";
	}

	@Override
	public boolean isConfigured() {
		return true;
	}

	@Override
	public GenerationResult generate(GenerationRequest request) {
		return new GenerationResult(
			demoAnswerComposer.compose(request.question(), request.retrievedChunks()),
			new GenerationMetadata(
				"mock",
				"demo-extractive",
				"development-only",
				properties.getTemperature(),
				properties.getMaxOutputTokens(),
				properties.getPromptVersion(),
				Instant.now(),
				null,
				null,
				null,
				null
			)
		);
	}
}
