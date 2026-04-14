package com.chat2b.admissions.model;

public record RetrievedChunk(
	long chunkId,
	long documentId,
	String documentTitle,
	String content,
	Integer pageNumber,
	String sectionName,
	double similarity
) {
}
