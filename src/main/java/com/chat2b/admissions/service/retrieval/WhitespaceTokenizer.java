package com.chat2b.admissions.service.retrieval;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class WhitespaceTokenizer implements RetrievalTokenizer {

	@Override
	public String name() {
		return "whitespace-v1";
	}

	@Override
	public List<String> tokenize(String text) {
		if (!StringUtils.hasText(text)) {
			return List.of();
		}
		return Arrays.stream(text.toLowerCase(Locale.ROOT).trim().split("\\s+"))
			.map(token -> token.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", ""))
			.filter(token -> token.length() >= 2)
			.toList();
	}
}
