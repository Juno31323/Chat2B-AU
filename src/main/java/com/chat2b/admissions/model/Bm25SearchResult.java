package com.chat2b.admissions.model;

import java.time.Instant;

public record Bm25SearchResult(
	long chunkId,
	long documentId,
	String title,
	String url,
	Instant postedAt,
	double bm25Score,
	int bm25Rank
) {
}
