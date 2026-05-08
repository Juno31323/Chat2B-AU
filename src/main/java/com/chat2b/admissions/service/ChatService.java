package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.model.ChatRequest;
import com.chat2b.admissions.model.ChatResponse;
import com.chat2b.admissions.model.GenerationRequest;
import com.chat2b.admissions.model.GenerationResult;
import com.chat2b.admissions.model.RetrievedChunk;
import com.chat2b.admissions.model.SourceReference;
import com.chat2b.admissions.repository.AdmissionsRepository;
import com.chat2b.admissions.service.generation.ChatModelRouter;
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
	private final ChatModelRouter chatModelRouter;
	private final RateLimitService rateLimitService;
	private final RefusalGuardService refusalGuardService;
	private final AppProperties appProperties;

	public ChatService(
		AdmissionsRepository repository,
		DocumentIngestionService documentIngestionService,
		GeminiGateway geminiGateway,
		HashedEmbeddingService hashedEmbeddingService,
		ChatModelRouter chatModelRouter,
		RateLimitService rateLimitService,
		RefusalGuardService refusalGuardService,
		AppProperties appProperties
	) {
		this.repository = repository;
		this.documentIngestionService = documentIngestionService;
		this.geminiGateway = geminiGateway;
		this.hashedEmbeddingService = hashedEmbeddingService;
		this.chatModelRouter = chatModelRouter;
		this.rateLimitService = rateLimitService;
		this.refusalGuardService = refusalGuardService;
		this.appProperties = appProperties;
	}

	public ChatResponse answer(ChatRequest request, String ipAddress) {
		rateLimitService.check(ipAddress, request.sessionId());

		Set<String> questionTokens = tokenize(request.question());
		List<RetrievedChunk> retrieved = retrieveDense(request.question(), appProperties.getRetrievalTopK());
		RefusalGuardService.RefusalDecision refusalDecision = refusalGuardService.evaluate(request.question(), retrieved);
		if (!refusalDecision.allowed()) {
			repository.logChat(
				request.question(),
				refusalDecision.message(),
				answerMode(),
				refusalDecision.status(),
				List.of(),
				null,
				ipAddress,
				request.sessionId()
			);
			return new ChatResponse(refusalDecision.message(), answerMode(), refusalDecision.status(), List.of(), null, Instant.now());
		}

		List<RetrievedChunk> matched = refusalDecision.candidates().stream()
			.filter(chunk -> chunk.similarity() >= appProperties.getMinSimilarity() || lexicalOverlap(questionTokens, chunk) > 0)
			.sorted(Comparator.comparingDouble(chunk -> -rankScore(questionTokens, chunk)))
			.toList();

		if (matched.isEmpty()) {
			String answer = "제공된 공지에서 확인되지 않습니다";
			repository.logChat(request.question(), answer, answerMode(), "NO_MATCH", List.of(), null, ipAddress, request.sessionId());
			return new ChatResponse(answer, answerMode(), "NO_MATCH", List.of(), null, Instant.now());
		}

		List<RetrievedChunk> contextChunks = trimContext(matched, appProperties.getMaxContextChars());
		List<SourceReference> sources = contextChunks.stream()
			.limit(responseSourceLimit())
			.map(this::toSource)
			.toList();

		GenerationResult generationResult = chatModelRouter.generate(
			new GenerationRequest(request.question(), contextChunks, Boolean.TRUE.equals(request.important()))
		);
		String answerMode = generationResult.metadata() == null
			? answerMode()
			: generationResult.metadata().provider();
		String answer = appendSourceSummary(generationResult.answer(), sources);

		repository.logChat(request.question(), answer, answerMode, "MATCHED", sources, generationResult.metadata(), ipAddress, request.sessionId());
		return new ChatResponse(answer, answerMode, "MATCHED", sources, generationResult.metadata(), Instant.now());
	}

	public List<RetrievedChunk> retrieveDense(String question, int limit) {
		float[] embedding = embed(question);
		return repository.searchSimilarChunks(
			embedding,
			Math.max(1, limit),
			documentIngestionService.currentEmbeddingModelName(),
			appProperties.getEmbeddingDimensions(),
			appProperties.getIndexVersion()
		);
	}

	private float[] embed(String question) {
		return documentIngestionService.usesGeminiEmbeddings()
			? geminiGateway.createQueryEmbedding(question)
			: hashedEmbeddingService.embed(question);
	}

	private String answerMode() {
		return chatModelRouter.answerMode();
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
		return new SourceReference(
			label.toString(),
			snippet,
			Math.round(chunk.similarity() * 1000.0d) / 1000.0d,
			chunk.documentTitle(),
			chunk.url(),
			chunk.postedAt()
		);
	}

	private String appendSourceSummary(String answer, List<SourceReference> sources) {
		if (sources.isEmpty()) {
			return answer;
		}
		StringBuilder builder = new StringBuilder(answer == null ? "" : answer.trim());
		builder.append("\n\n근거:");
		for (SourceReference source : sources) {
			builder
				.append("\n- 제목: ").append(source.title())
				.append("\n  게시일: ").append(source.postedAt() == null ? "게시일 미상" : source.postedAt())
				.append("\n  URL: ").append(StringUtils.hasText(source.url()) ? source.url() : "URL 없음");
		}
		return builder.toString();
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
