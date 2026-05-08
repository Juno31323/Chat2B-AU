package com.chat2b.admissions.service.retrieval;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class UnicodeKoreanTokenizer implements RetrievalTokenizer {

	private static final List<String> KOREAN_SUFFIXES = List.of(
		"으로", "에서", "에게", "까지", "부터", "처럼", "보다",
		"은", "는", "이", "가", "을", "를", "에", "의", "도", "만", "와", "과", "로"
	);

	@Override
	public String name() {
		return "unicode-korean-v1";
	}

	@Override
	public List<String> tokenize(String text) {
		if (!StringUtils.hasText(text)) {
			return List.of();
		}
		return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
			.map(this::normalize)
			.filter(token -> token.length() >= 2)
			.toList();
	}

	private String normalize(String token) {
		for (String suffix : KOREAN_SUFFIXES) {
			if (token.length() > suffix.length() + 1 && token.endsWith(suffix)) {
				return token.substring(0, token.length() - suffix.length());
			}
		}
		return token;
	}
}
