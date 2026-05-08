package com.chat2b.admissions.model;

public record GenerationResult(
	String answer,
	GenerationMetadata metadata
) {
}
