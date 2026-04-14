package com.chat2b.admissions.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TextTokenUtils {

	private static final List<String> SUFFIXES = List.of(
		"으로", "에서", "에게", "까지", "부터", "처럼", "보다",
		"은", "는", "이", "가", "을", "를", "의", "에", "도", "만", "과", "와", "로", "요", "야"
	);

	private TextTokenUtils() {
	}

	public static Set<String> tokenize(String value) {
		Set<String> tokens = new LinkedHashSet<>();
		for (String rawToken : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
			if (rawToken.length() < 2) {
				continue;
			}
			String normalized = normalize(rawToken);
			if (normalized.length() >= 2) {
				tokens.add(normalized);
			}
		}
		return tokens;
	}

	public static String normalize(String token) {
		for (String suffix : SUFFIXES) {
			if (token.length() > suffix.length() + 1 && token.endsWith(suffix)) {
				return token.substring(0, token.length() - suffix.length());
			}
		}
		return token;
	}
}
