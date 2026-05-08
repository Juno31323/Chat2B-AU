package com.chat2b.admissions.model;

import java.time.Instant;

public record IndexMetadata(
	long id,
	String indexName,
	String corpusProfile,
	String indexVersion,
	int documentCount,
	int chunkCount,
	String embeddingModel,
	int embeddingDim,
	int chunkSize,
	int chunkOverlap,
	String tokenizer,
	String corpusHash,
	String retrievalConfigHash,
	String sourceDataPath,
	Instant createdAt
) {
}
