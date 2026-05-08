package com.chat2b.admissions.model;

import java.time.Instant;

public record Bm25IndexMetadata(
	long id,
	String corpusProfile,
	String indexVersion,
	String tokenizer,
	int documentCount,
	int chunkCount,
	String corpusHash,
	Instant createdAt
) {
}
