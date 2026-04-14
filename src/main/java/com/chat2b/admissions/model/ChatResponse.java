package com.chat2b.admissions.model;

import java.time.Instant;
import java.util.List;

public record ChatResponse(
	String answer,
	String answerMode,
	String retrievalStatus,
	List<SourceReference> sources,
	Instant generatedAt
) {
}
