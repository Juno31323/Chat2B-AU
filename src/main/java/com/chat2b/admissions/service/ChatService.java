package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.model.ChatRequest;
import com.chat2b.admissions.model.ChatResponse;
import com.chat2b.admissions.model.RetrievedChunk;
import com.chat2b.admissions.model.SourceReference;
import com.chat2b.admissions.repository.AdmissionsRepository;
import com.chat2b.admissions.support.TextTokenUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class ChatService {

	private final AdmissionsRepository repository;
	private final DocumentIngestionService documentIngestionService;
	private final GeminiGateway geminiGateway;
	private final HashedEmbeddingService hashedEmbeddingService;
	private final DemoAnswerComposer demoAnswerComposer;
	private final RateLimitService rateLimitService;
	private final AppProperties appProperties;

	public ChatService(
		AdmissionsRepository repository,
		DocumentIngestionService documentIngestionService,
		GeminiGateway geminiGateway,
		HashedEmbeddingService hashedEmbeddingService,
		DemoAnswerComposer demoAnswerComposer,
		RateLimitService rateLimitService,
		AppProperties appProperties
	) {
		this.repository = repository;
		this.documentIngestionService = documentIngestionService;
		this.geminiGateway = geminiGateway;
		this.hashedEmbeddingService = hashedEmbeddingService;
		this.demoAnswerComposer = demoAnswerComposer;
		this.rateLimitService = rateLimitService;
		this.appProperties = appProperties;
	}

	public ChatResponse answer(ChatRequest request, String ipAddress) {
		rateLimitService.check(ipAddress, request.sessionId());

		float[] embedding = embed(request.question());
		Set<String> questionTokens = tokenize(request.question());
		List<RetrievedChunk> retrieved = repository.searchSimilarChunks(embedding, appProperties.getRetrievalTopK());
		List<RetrievedChunk> matched = retrieved.stream()
			.filter(chunk -> chunk.similarity() >= appProperties.getMinSimilarity() || lexicalOverlap(questionTokens, chunk) > 0)
			.sorted(Comparator.comparingDouble(chunk -> -rankScore(questionTokens, chunk)))
			.toList();

		if (matched.isEmpty()) {
			String answer = "제공된 모집요강과 FAQ 기준으로는 질문에 대한 근거를 찾지 못했습니다. 입학처로 직접 문의해 주세요.";
			repository.logChat(request.question(), answer, answerMode(), "NO_MATCH", List.of(), ipAddress, request.sessionId());
			return new ChatResponse(answer, answerMode(), "NO_MATCH", List.of(), Instant.now());
		}

		List<RetrievedChunk> contextChunks = trimContext(matched, appProperties.getMaxContextChars());
		List<SourceReference> sources = contextChunks.stream()
			.limit(responseSourceLimit())
			.map(this::toSource)
			.toList();

		String answerMode = answerMode();
		String answer;
		try {
			answer = geminiGateway.isConfigured()
				? geminiGateway.generateAnswer(request.question(), contextChunks)
				: demoAnswerComposer.compose(request.question(), contextChunks);
		} catch (RuntimeException exception) {
			answer = demoAnswerComposer.compose(request.question(), contextChunks);
			answerMode = "demo-fallback";
		}

		repository.logChat(request.question(), answer, answerMode, "MATCHED", sources, ipAddress, request.sessionId());
		return new ChatResponse(answer, answerMode, "MATCHED", sources, Instant.now());
	}

	private float[] embed(String question) {
		return documentIngestionService.usesGeminiEmbeddings()
			? geminiGateway.createQueryEmbedding(question)
			: hashedEmbeddingService.embed(question);
	}

	private String answerMode() {
		return geminiGateway.isConfigured() ? "gemini" : "demo";
	}

	private int responseSourceLimit() {
		return Math.max(1, appProperties.getResponseSourceLimit());
	}

	private List<RetrievedChunk> trimContext(List<RetrievedChunk> chunks, int maxContextChars) {
		List<RetrievedChunk> selected = new ArrayList<>();
		int currentLength = 0;
		for (RetrievedChunk chunk : chunks) {
			if (!selected.isEmpty() && currentLength + chunk.content().length() > maxContextChars) {
				break;
			}
			selected.add(chunk);
			currentLength += chunk.content().length();
		}
		return selected.isEmpty() ? List.of(chunks.get(0)) : selected;
	}

	private SourceReference toSource(RetrievedChunk chunk) {
		StringBuilder label = new StringBuilder(chunk.documentTitle());
		if (chunk.pageNumber() != null) {
			label.append(" / ").append(chunk.pageNumber()).append("p");
		}
		if (StringUtils.hasText(chunk.sectionName())) {
			label.append(" / ").append(chunk.sectionName());
		}

		String snippet = chunk.content().replaceAll("\\s+", " ").trim();
		if (snippet.length() > 180) {
			snippet = snippet.substring(0, 180) + "...";
		}
		return new SourceReference(label.toString(), snippet, Math.round(chunk.similarity() * 1000.0d) / 1000.0d);
	}

	private double rankScore(Set<String> questionTokens, RetrievedChunk chunk) {
		long overlap = lexicalOverlap(questionTokens, chunk);
		return overlap * 10.0d + chunk.similarity();
	}

	private long lexicalOverlap(Set<String> questionTokens, RetrievedChunk chunk) {
		String searchableText = String.join(
			" ",
			chunk.documentTitle(),
			chunk.sectionName() == null ? "" : chunk.sectionName(),
			chunk.content()
		);
		Set<String> chunkTokens = tokenize(searchableText);
		return questionTokens.stream().filter(chunkTokens::contains).count();
	}

	private Set<String> tokenize(String value) {
		return TextTokenUtils.tokenize(value);
	}
}
