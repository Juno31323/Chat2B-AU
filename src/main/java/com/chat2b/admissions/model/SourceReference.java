package com.chat2b.admissions.model;

import java.time.Instant;

public record SourceReference(
	String label,
	String snippet,
	double score,
	String title,
	String url,
	Instant postedAt
) {
}
