package com.chat2b.admissions.model;

import java.time.Instant;

public record Bm25Chunk(
	long chunkId,
	long documentId,
	String noticeId,
	String title,
	String url,
	Instant postedAt,
	String content
) {
}
