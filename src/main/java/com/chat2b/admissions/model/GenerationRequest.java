package com.chat2b.admissions.model;

import java.util.List;

public record GenerationRequest(
	String question,
	List<RetrievedChunk> retrievedChunks,
	boolean important
) {
}
