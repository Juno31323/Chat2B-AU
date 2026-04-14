package com.chat2b.admissions.service;

import com.chat2b.admissions.model.RetrievedChunk;
import com.chat2b.admissions.support.TextTokenUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DemoAnswerComposer {

	private static final Pattern SPLIT_PATTERN = Pattern.compile("(?<=[.!?])\\s+|\\n");

	public String compose(String question, List<RetrievedChunk> chunks) {
		Set<String> questionTokens = tokenize(question);
		List<ScoredSentence> candidates = chunks.stream()
			.flatMap(chunk -> SPLIT_PATTERN.splitAsStream(chunk.content())
				.map(String::trim)
				.filter(StringUtils::hasText)
				.map(sentence -> new ScoredSentence(sentence, score(questionTokens, sentence, chunk.similarity()))))
			.sorted(Comparator.comparingDouble(ScoredSentence::score).reversed())
			.toList();

		LinkedHashSet<String> selected = new LinkedHashSet<>();
		for (ScoredSentence candidate : candidates) {
			selected.add(candidate.text());
			if (selected.size() == 3) {
				break;
			}
		}

		if (selected.isEmpty()) {
			return "제공된 모집요강과 FAQ 기준으로는 질문에 대한 근거를 충분히 찾지 못했습니다. 입학처에 직접 문의해 주세요.";
		}

		String answer = String.join(" ", selected);
		if (!answer.endsWith(".") && !answer.endsWith("다.") && !answer.endsWith("요.")) {
			answer = answer + "입니다.";
		}
		return answer;
	}

	private double score(Set<String> questionTokens, String sentence, double similarity) {
		Set<String> sentenceTokens = tokenize(sentence);
		long overlap = questionTokens.stream().filter(sentenceTokens::contains).count();
		return overlap * 2.0d + similarity;
	}

	private Set<String> tokenize(String value) {
		return new LinkedHashSet<>(TextTokenUtils.tokenize(value));
	}

	private record ScoredSentence(String text, double score) {
	}
}
