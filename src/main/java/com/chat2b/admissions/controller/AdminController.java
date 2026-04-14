package com.chat2b.admissions.controller;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.exception.AdminAccessDeniedException;
import com.chat2b.admissions.exception.AdminOperationThrottledException;
import com.chat2b.admissions.repository.AdmissionsRepository;
import com.chat2b.admissions.service.DocumentIngestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
	private final AtomicBoolean reindexInProgress = new AtomicBoolean(false);
	private final AtomicLong lastReindexStartedAt = new AtomicLong(Long.MIN_VALUE);

	public AdminController(
		AppProperties appProperties,
		AdmissionsRepository repository,
		DocumentIngestionService documentIngestionService
	) {
		this.appProperties = appProperties;
		this.repository = repository;
		this.documentIngestionService = documentIngestionService;
	}

	@GetMapping("/status")
	public Map<String, Object> status(@RequestHeader("X-Admin-Key") String adminKey) {
		requireAdminKey(adminKey);
		Map<String, Object> status = new LinkedHashMap<>(repository.getStatusSummary());
		status.put("bootstrapLocation", appProperties.getBootstrapLocation());
		status.put("embeddingMode", documentIngestionService.embeddingMode());
		status.put("adminConfigured", appProperties.hasConfiguredAdminKey());
		status.put("reindexCooldownSeconds", appProperties.getAdminReindexCooldownSeconds());
		return status;
	}

	@PostMapping("/reindex")
	public Map<String, Object> reindex(@RequestHeader("X-Admin-Key") String adminKey) {
		requireAdminKey(adminKey);
		enforceReindexCooldown();
		if (!reindexInProgress.compareAndSet(false, true)) {
			throw new AdminOperationThrottledException("A reindex is already running.");
		}
		try {
			var summary = documentIngestionService.reindex(appProperties.getBootstrapLocation());
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("documents", summary.documentCount());
			result.put("chunks", summary.chunkCount());
			result.put("embeddingMode", summary.embeddingMode());
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
}
