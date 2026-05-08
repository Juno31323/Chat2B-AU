package com.chat2b.admissions.model;

import java.time.Instant;

public record GenerationMetadata(
	String provider,
	String model,
	String modelVersion,
	double temperature,
	int maxOutputTokens,
	String promptVersion,
	Instant runDate,
	Integer inputTokens,
	Integer outputTokens,
	Integer totalTokens,
	Double estimatedCostUsd
) {
}
