package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.model.Bm25SearchResult;
import com.chat2b.admissions.model.HybridSearchResult;
import com.chat2b.admissions.model.RetrievedChunk;
import com.chat2b.admissions.repository.AdmissionsRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HybridRetrievalService {

	private static final String RRF = "rrf";

	private final AppProperties appProperties;
	private final Bm25SearchService bm25SearchService;
	private final ChatService chatService;
	private final AdmissionsRepository repository;

	public HybridRetrievalService(
		AppProperties appProperties,
		Bm25SearchService bm25SearchService,
		ChatService chatService,
		AdmissionsRepository repository
	) {
		this.appProperties = appProperties;
		this.bm25SearchService = bm25SearchService;
		this.chatService = chatService;
		this.repository = repository;
	}

	public List<HybridSearchResult> search(String question) {
		return search(
			question,
			appProperties.getHybridTopK(),
			appProperties.getBm25TopK(),
			appProperties.getDenseTopK(),
			appProperties.getRrfK(),
			appProperties.getFusionMethod()
		);
	}

	public List<HybridSearchResult> search(
		String question,
		int topK,
		int bm25TopK,
		int denseTopK,
		int rrfK,
		String fusionMethod
	) {
		String normalizedFusionMethod = normalizeFusionMethod(fusionMethod);
		List<Bm25SearchResult> bm25Results = bm25SearchService.search(question, Math.max(1, bm25TopK));
		List<RetrievedChunk> denseResults = chatService.retrieveDense(question, Math.max(1, denseTopK));

		Map<Long, Candidate> candidates = new LinkedHashMap<>();
		for (Bm25SearchResult bm25 : bm25Results) {
			Candidate candidate = candidates.computeIfAbsent(bm25.chunkId(), ignored -> Candidate.fromBm25(bm25));
			candidate.applyBm25(bm25);
		}
		for (RetrievedChunk dense : denseResults) {
			Candidate candidate = candidates.computeIfAbsent(dense.chunkId(), ignored -> Candidate.fromDense(dense));
			candidate.applyDense(dense);
		}

		List<Candidate> rankedCandidates = candidates.values().stream()
			.peek(candidate -> candidate.hybridScore = rrfScore(candidate, Math.max(1, rrfK)))
			.sorted(Comparator.comparingDouble(Candidate::hybridScore).reversed())
			.limit(Math.max(1, topK))
			.toList();

		List<HybridSearchResult> results = new ArrayList<>();
		for (int index = 0; index < rankedCandidates.size(); index++) {
			Candidate candidate = rankedCandidates.get(index);
			results.add(new HybridSearchResult(
				candidate.chunkId,
				candidate.documentId,
				candidate.noticeId,
				candidate.title,
				candidate.url,
				candidate.postedAt,
				candidate.bm25Score,
				candidate.denseScore,
				candidate.bm25Rank,
				candidate.denseRank,
				round(candidate.hybridScore),
				index + 1,
				"hybrid-" + normalizedFusionMethod
			));
		}
		repository.logHybridRetrieval(question, appProperties.getIndexVersion(), normalizedFusionMethod, results);
		return results;
	}

	private String normalizeFusionMethod(String fusionMethod) {
		String normalized = fusionMethod == null ? RRF : fusionMethod.trim().toLowerCase(Locale.ROOT);
		if (!RRF.equals(normalized)) {
			throw new IllegalArgumentException("Only rrf fusion is currently supported.");
		}
		return normalized;
	}

	private double rrfScore(Candidate candidate, int rrfK) {
		double score = 0.0d;
		if (candidate.bm25Rank != null) {
			score += 1.0d / (rrfK + candidate.bm25Rank);
		}
		if (candidate.denseRank != null) {
			score += 1.0d / (rrfK + candidate.denseRank);
		}
		return score;
	}

	private double round(double value) {
		return Math.round(value * 1_000_000.0d) / 1_000_000.0d;
	}

	private static final class Candidate {
		private long chunkId;
		private long documentId;
		private String noticeId;
		private String title;
		private String url;
		private Instant postedAt;
		private Double bm25Score;
		private Double denseScore;
		private Integer bm25Rank;
		private Integer denseRank;
		private double hybridScore;

		private static Candidate fromBm25(Bm25SearchResult bm25) {
			Candidate candidate = new Candidate();
			candidate.chunkId = bm25.chunkId();
			candidate.documentId = bm25.documentId();
			candidate.noticeId = bm25.noticeId();
			candidate.title = bm25.title();
			candidate.url = bm25.url();
			candidate.postedAt = bm25.postedAt();
			return candidate;
		}

		private static Candidate fromDense(RetrievedChunk dense) {
			Candidate candidate = new Candidate();
			candidate.chunkId = dense.chunkId();
			candidate.documentId = dense.documentId();
			candidate.noticeId = dense.noticeId();
			candidate.title = dense.documentTitle();
			candidate.url = dense.url();
			candidate.postedAt = dense.postedAt();
			return candidate;
		}

		private void applyBm25(Bm25SearchResult bm25) {
			bm25Score = bm25.bm25Score();
			bm25Rank = bm25.bm25Rank();
			fillMissing(bm25.noticeId(), bm25.title(), bm25.url(), bm25.postedAt(), bm25.documentId());
		}

		private void applyDense(RetrievedChunk dense) {
			denseScore = dense.denseScore();
			denseRank = dense.denseRank();
			fillMissing(dense.noticeId(), dense.documentTitle(), dense.url(), dense.postedAt(), dense.documentId());
		}

		private void fillMissing(String candidateNoticeId, String candidateTitle, String candidateUrl, Instant candidatePostedAt, long candidateDocumentId) {
			if (noticeId == null) {
				noticeId = candidateNoticeId;
			}
			if (title == null) {
				title = candidateTitle;
			}
			if (url == null) {
				url = candidateUrl;
			}
			if (postedAt == null) {
				postedAt = candidatePostedAt;
			}
			if (documentId == 0L) {
				documentId = candidateDocumentId;
			}
		}

		private double hybridScore() {
			return hybridScore;
		}
	}
}
