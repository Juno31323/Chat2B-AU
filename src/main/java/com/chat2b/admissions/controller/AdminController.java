package com.chat2b.admissions.controller;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.exception.AdminAccessDeniedException;
import com.chat2b.admissions.exception.AdminOperationThrottledException;
import com.chat2b.admissions.repository.AdmissionsRepository;
import com.chat2b.admissions.service.Bm25SearchService;
import com.chat2b.admissions.service.ChatService;
import com.chat2b.admissions.service.DenseVectorSchemaService;
import com.chat2b.admissions.service.DocumentIngestionService;
import com.chat2b.admissions.service.HybridRetrievalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final AppProperties appProperties;
	private final AdmissionsRepository repository;
	private final DocumentIngestionService documentIngestionService;
	private final DenseVectorSchemaService denseVectorSchemaService;
	private final Bm25SearchService bm25SearchService;
	private final HybridRetrievalService hybridRetrievalService;
	private final ChatService chatService;
	private final AtomicBoolean reindexInProgress = new AtomicBoolean(false);
	private final AtomicLong lastReindexStartedAt = new AtomicLong(Long.MIN_VALUE);

	public AdminController(
		AppProperties appProperties,
		AdmissionsRepository repository,
		DocumentIngestionService documentIngestionService,
		DenseVectorSchemaService denseVectorSchemaService,
		Bm25SearchService bm25SearchService,
		HybridRetrievalService hybridRetrievalService,
		ChatService chatService
	) {
		this.appProperties = appProperties;
		this.repository = repository;
		this.documentIngestionService = documentIngestionService;
		this.denseVectorSchemaService = denseVectorSchemaService;
		this.bm25SearchService = bm25SearchService;
		this.hybridRetrievalService = hybridRetrievalService;
		this.chatService = chatService;
	}

	@GetMapping("/status")
	public Map<String, Object> status(@RequestHeader("X-Admin-Key") String adminKey) {
		requireAdminKey(adminKey);
		Map<String, Object> status = new LinkedHashMap<>(repository.getStatusSummary());
		status.put("bootstrapLocation", appProperties.getBootstrapLocation());
		status.put("indexName", appProperties.getIndexName());
		status.put("corpusProfile", appProperties.getCorpusProfile());
		status.put("indexVersion", appProperties.getIndexVersion());
		status.put("retrievalConfigHash", documentIngestionService.retrievalConfigHash());
		status.put("embeddingMode", documentIngestionService.embeddingMode());
		status.put("denseRetrievalMode", denseVectorSchemaService.retrievalMode());
		status.put("fusionMethod", appProperties.getFusionMethod());
		status.put("hybridTopK", appProperties.getHybridTopK());
		status.put("bm25TopK", appProperties.getBm25TopK());
		status.put("denseTopK", appProperties.getDenseTopK());
		status.put("rrfK", appProperties.getRrfK());
		status.put("pgvectorAvailable", denseVectorSchemaService.isPgvectorAvailable());
		repository.getLatestIndexMetadata().ifPresent(metadata -> status.put("indexMetadata", metadata));
		repository.getLatestBm25IndexMetadata().ifPresent(metadata -> status.put("bm25IndexMetadata", metadata));
		status.put("adminConfigured", appProperties.hasConfiguredAdminKey());
		status.put("reindexCooldownSeconds", appProperties.getAdminReindexCooldownSeconds());
		return status;
	}

	@GetMapping("/dense-search")
	public Map<String, Object> denseSearch(
		@RequestHeader("X-Admin-Key") String adminKey,
		@RequestParam String question,
		@RequestParam(defaultValue = "5") int limit
	) {
		requireAdminKey(adminKey);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("question", question);
		result.put("denseRetrievalMode", denseVectorSchemaService.retrievalMode());
		result.put("embeddingModel", documentIngestionService.currentEmbeddingModelName());
		result.put("embeddingDim", appProperties.getEmbeddingDimensions());
		result.put("indexVersion", appProperties.getIndexVersion());
		result.put("results", chatService.retrieveDense(question, Math.min(Math.max(limit, 1), 20)).stream()
			.map(chunk -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("chunk_id", chunk.chunkId());
				item.put("document_id", chunk.documentId());
				item.put("title", chunk.documentTitle());
				item.put("url", chunk.url());
				item.put("posted_at", chunk.postedAt());
				item.put("dense_score", chunk.denseScore());
				item.put("dense_rank", chunk.denseRank());
				return item;
			})
			.toList());
		return result;
	}

	@GetMapping("/hybrid-search")
	public Map<String, Object> hybridSearch(
		@RequestHeader("X-Admin-Key") String adminKey,
		@RequestParam String question,
		@RequestParam(required = false) Integer topK,
		@RequestParam(required = false) Integer bm25TopK,
		@RequestParam(required = false) Integer denseTopK,
		@RequestParam(required = false) Integer rrfK
	) {
		requireAdminKey(adminKey);
		int resolvedTopK = clamp(topK, appProperties.getHybridTopK(), 1, 50);
		int resolvedBm25TopK = clamp(bm25TopK, appProperties.getBm25TopK(), 1, 500);
		int resolvedDenseTopK = clamp(denseTopK, appProperties.getDenseTopK(), 1, 500);
		int resolvedRrfK = clamp(rrfK, appProperties.getRrfK(), 1, 1000);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("question", question);
		result.put("retrievalMethod", "hybrid-" + appProperties.getFusionMethod());
		result.put("fusionMethod", appProperties.getFusionMethod());
		result.put("topK", resolvedTopK);
		result.put("bm25TopK", resolvedBm25TopK);
		result.put("denseTopK", resolvedDenseTopK);
		result.put("rrfK", resolvedRrfK);
		result.put("indexVersion", appProperties.getIndexVersion());
		result.put("results", hybridRetrievalService.search(
			question,
			resolvedTopK,
			resolvedBm25TopK,
			resolvedDenseTopK,
			resolvedRrfK,
			appProperties.getFusionMethod()
		).stream()
			.map(chunk -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("chunk_id", chunk.chunkId());
				item.put("document_id", chunk.documentId());
				item.put("title", chunk.title());
				item.put("url", chunk.url());
				item.put("posted_at", chunk.postedAt());
				item.put("bm25_rank", chunk.bm25Rank());
				item.put("dense_rank", chunk.denseRank());
				item.put("bm25_score", chunk.bm25Score());
				item.put("dense_score", chunk.denseScore());
				item.put("hybrid_score", chunk.hybridScore());
				item.put("hybrid_rank", chunk.hybridRank());
				item.put("retrieval_method", chunk.retrievalMethod());
				return item;
			})
			.toList());
		return result;
	}

	@GetMapping("/bm25-search")
	public Map<String, Object> bm25Search(
		@RequestHeader("X-Admin-Key") String adminKey,
		@RequestParam String question,
		@RequestParam(defaultValue = "5") int limit
	) {
		requireAdminKey(adminKey);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("question", question);
		result.put("tokenizer", bm25SearchService.currentTokenizerName());
		result.put("indexVersion", appProperties.getIndexVersion());
		result.put("results", bm25SearchService.search(question, Math.min(Math.max(limit, 1), 20)).stream()
			.map(chunk -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("chunk_id", chunk.chunkId());
				item.put("document_id", chunk.documentId());
				item.put("title", chunk.title());
				item.put("url", chunk.url());
				item.put("posted_at", chunk.postedAt());
				item.put("bm25_score", chunk.bm25Score());
				item.put("bm25_rank", chunk.bm25Rank());
				return item;
			})
			.toList());
		return result;
	}

	@PostMapping("/reindex")
	public Map<String, Object> reindex(
		@RequestHeader("X-Admin-Key") String adminKey,
		@RequestParam(defaultValue = "true") boolean force
	) {
		requireAdminKey(adminKey);
		enforceReindexCooldown();
		if (!reindexInProgress.compareAndSet(false, true)) {
			throw new AdminOperationThrottledException("A reindex is already running.");
		}
		try {
			var decision = documentIngestionService.evaluateReindex(appProperties.getBootstrapLocation(), force);
			if (!decision.required()) {
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("force", force);
				result.put("reindexed", false);
				result.put("reindexReason", decision.reason());
				result.put("expectedMetadata", decision.expected());
				result.put("indexMetadata", decision.actual());
				return result;
			}
			var summary = documentIngestionService.reindex(appProperties.getBootstrapLocation());
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("force", force);
			result.put("reindexed", true);
			result.put("reindexReason", decision.reason());
			result.put("documents", summary.documentCount());
			result.put("chunks", summary.chunkCount());
			result.put("embeddingMode", summary.embeddingMode());
			repository.getLatestIndexMetadata().ifPresent(metadata -> result.put("indexMetadata", metadata));
			return result;
		} finally {
			reindexInProgress.set(false);
		}
	}

	private void requireAdminKey(String adminKey) {
		if (!appProperties.hasConfiguredAdminKey()) {
			throw new AdminAccessDeniedException("Admin API is disabled until APP_ADMIN_KEY is configured.");
		}
		byte[] configuredKey = appProperties.getAdminKey().trim().getBytes(StandardCharsets.UTF_8);
		byte[] requestKey = adminKey == null ? new byte[0] : adminKey.trim().getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(configuredKey, requestKey)) {
			throw new AdminAccessDeniedException("Admin key is invalid.");
		}
	}

	private void enforceReindexCooldown() {
		long cooldownMillis = Math.max(0, appProperties.getAdminReindexCooldownSeconds()) * 1000L;
		if (cooldownMillis == 0) {
			return;
		}

		long now = System.currentTimeMillis();
		while (true) {
			long previous = lastReindexStartedAt.get();
			if (previous != Long.MIN_VALUE && now - previous < cooldownMillis) {
				long remainingSeconds = Math.max(1, (cooldownMillis - (now - previous) + 999L) / 1000L);
				throw new AdminOperationThrottledException(
					"Reindex is temporarily locked. Try again in %d seconds.".formatted(remainingSeconds)
				);
			}
			if (lastReindexStartedAt.compareAndSet(previous, now)) {
				return;
			}
		}
	}

	private int clamp(Integer value, int fallback, int min, int max) {
		int resolved = value == null ? fallback : value;
		return Math.min(max, Math.max(min, resolved));
	}
}
