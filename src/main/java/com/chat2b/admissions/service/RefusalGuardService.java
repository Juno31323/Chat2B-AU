package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.model.RetrievedChunk;
import com.chat2b.admissions.support.TextTokenUtils;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RefusalGuardService {

	private static final String REFUSAL_MESSAGE = "제공된 공지에서 확인되지 않습니다. 입학처에 직접 문의해 주세요.";
	private static final List<String> HARD_OUT_OF_DOMAIN_CUES = List.of(
		"날씨", "학번", "삼성전자", "주가", "2030학년도", "존재하지", "개인 연락처"
	);

	private final AppProperties appProperties;

	public RefusalGuardService(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	public RefusalDecision evaluate(String question, List<RetrievedChunk> retrievedChunks) {
		if (retrievedChunks == null || retrievedChunks.isEmpty()) {
			return RefusalDecision.refused("NO_RETRIEVAL", REFUSAL_MESSAGE, List.of(), 0.0d, 0, 0.0d);
		}

		List<RetrievedChunk> sorted = retrievedChunks.stream()
			.sorted(Comparator.comparingDouble(RetrievedChunk::denseScore).reversed())
			.toList();
		double topDenseScore = sorted.get(0).denseScore();
		if (topDenseScore < appProperties.getRefusalMinDenseScore()) {
			return RefusalDecision.refused("LOW_DENSE_SCORE", REFUSAL_MESSAGE, List.of(), topDenseScore, 0, 0.0d);
		}

		Set<String> questionTokens = guardTokens(question);
		if (questionTokens.isEmpty()) {
			return RefusalDecision.refused("EMPTY_QUERY_TOKENS", REFUSAL_MESSAGE, List.of(), topDenseScore, 0, 0.0d);
		}

		int evidenceTopK = Math.max(1, appProperties.getRefusalEvidenceTopK());
		List<RetrievedChunk> evidenceCandidates = sorted.stream()
			.limit(evidenceTopK)
			.filter(chunk -> isRelevant(questionTokens, chunk))
			.toList();

		RelevanceSummary bestSummary = sorted.stream()
			.limit(evidenceTopK)
			.map(chunk -> summarize(questionTokens, chunk))
			.max(Comparator.comparingDouble(RelevanceSummary::coverage).thenComparingInt(RelevanceSummary::overlap))
			.orElse(new RelevanceSummary(0, 0.0d));

		if (containsOutOfDomainCue(question) && bestSummary.coverage() < 1.0d) {
			return RefusalDecision.refused(
				"OUT_OF_DOMAIN_CUE",
				REFUSAL_MESSAGE,
				List.of(),
				topDenseScore,
				bestSummary.overlap(),
				bestSummary.coverage()
			);
		}

		if (evidenceCandidates.isEmpty()) {
			return RefusalDecision.refused(
				"LOW_LEXICAL_RELEVANCE",
				REFUSAL_MESSAGE,
				List.of(),
				topDenseScore,
				bestSummary.overlap(),
				bestSummary.coverage()
			);
		}

		return RefusalDecision.allowed("MATCHED", evidenceCandidates, topDenseScore, bestSummary.overlap(), bestSummary.coverage());
	}

	private boolean isRelevant(Set<String> questionTokens, RetrievedChunk chunk) {
		RelevanceSummary summary = summarize(questionTokens, chunk);
		return summary.overlap() >= Math.max(1, appProperties.getRefusalMinTokenOverlap())
			&& summary.coverage() >= appProperties.getRefusalMinTokenCoverage();
	}

	private RelevanceSummary summarize(Set<String> questionTokens, RetrievedChunk chunk) {
		Set<String> chunkTokens = guardTokens(String.join(
			" ",
			chunk.documentTitle(),
			chunk.sectionName() == null ? "" : chunk.sectionName(),
			chunk.content()
		));
		int overlap = (int) questionTokens.stream().filter(chunkTokens::contains).count();
		double coverage = questionTokens.isEmpty() ? 0.0d : overlap / (double) questionTokens.size();
		return new RelevanceSummary(overlap, coverage);
	}

	private Set<String> guardTokens(String value) {
		Set<String> tokens = new LinkedHashSet<>(TextTokenUtils.tokenize(value));
		String normalizedValue = value == null ? "" : value.toLowerCase(Locale.ROOT);
		for (String rawToken : normalizedValue.split("[^\\p{L}\\p{N}]+")) {
			if (rawToken.length() >= 2) {
				tokens.add(rawToken);
			}
		}
		return tokens;
	}

	private boolean containsOutOfDomainCue(String question) {
		String normalizedQuestion = question == null ? "" : question.toLowerCase(Locale.ROOT);
		List<String> cues = new java.util.ArrayList<>(HARD_OUT_OF_DOMAIN_CUES);
		cues.addAll(appProperties.getRefusalOutOfDomainCues());
		return cues.stream()
			.filter(cue -> cue != null && !cue.isBlank())
			.map(cue -> cue.toLowerCase(Locale.ROOT).trim())
			.anyMatch(normalizedQuestion::contains);
	}

	public record RefusalDecision(
		boolean allowed,
		String status,
		String message,
		List<RetrievedChunk> candidates,
		double topDenseScore,
		int bestTokenOverlap,
		double bestTokenCoverage
	) {

		private static RefusalDecision allowed(
			String status,
			List<RetrievedChunk> candidates,
			double topDenseScore,
			int bestTokenOverlap,
			double bestTokenCoverage
		) {
			return new RefusalDecision(true, status, "", candidates, topDenseScore, bestTokenOverlap, bestTokenCoverage);
		}

		private static RefusalDecision refused(
			String status,
			String message,
			List<RetrievedChunk> candidates,
			double topDenseScore,
			int bestTokenOverlap,
			double bestTokenCoverage
		) {
			return new RefusalDecision(false, status, message, candidates, topDenseScore, bestTokenOverlap, bestTokenCoverage);
		}
	}

	private record RelevanceSummary(int overlap, double coverage) {
	}
}
