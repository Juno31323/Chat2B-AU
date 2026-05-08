package com.chat2b.admissions.service.retrieval;

import com.chat2b.admissions.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RetrievalTokenizerFactory {

	private final AppProperties appProperties;

	public RetrievalTokenizerFactory(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	public RetrievalTokenizer current() {
		String tokenizer = appProperties.getTokenizer();
		if (tokenizer != null && tokenizer.toLowerCase(Locale.ROOT).startsWith("whitespace")) {
			return new WhitespaceTokenizer();
		}
		return new UnicodeKoreanTokenizer();
	}
}
