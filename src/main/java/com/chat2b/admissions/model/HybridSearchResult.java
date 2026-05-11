package com.chat2b.admissions.model;

import java.time.Instant;

public record HybridSearchResult(
	long chunkId,
	long documentId,
	String noticeId,
	String title,
	String url,
	Instant postedAt,
	Double bm25Score,
	Double denseScore,
	Integer bm25Rank,
	Integer denseRank,
	double hybridScore,
	int hybridRank,
	String retrievalMethod
) {
}
