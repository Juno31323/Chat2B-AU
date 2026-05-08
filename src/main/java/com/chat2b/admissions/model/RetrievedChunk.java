package com.chat2b.admissions.model;

import java.time.Instant;

public record RetrievedChunk(
	long chunkId,
	long documentId,
	String documentTitle,
	String url,
	Instant postedAt,
	String content,
	Integer pageNumber,
	String sectionName,
	double denseScore,
	int denseRank
) {

	public double similarity() {
		return denseScore;
	}
}
