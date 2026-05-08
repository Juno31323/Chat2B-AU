package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.model.Bm25Chunk;
import com.chat2b.admissions.model.Bm25SearchResult;
import com.chat2b.admissions.repository.AdmissionsRepository;
import com.chat2b.admissions.service.retrieval.RetrievalTokenizer;
import com.chat2b.admissions.service.retrieval.RetrievalTokenizerFactory;
import com.chat2b.admissions.support.CorpusHashUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class Bm25SearchService {

	private static final double K1 = 1.5d;
	private static final double B = 0.75d;

	private final AdmissionsRepository repository;
	private final AppProperties appProperties;
	private final RetrievalTokenizerFactory tokenizerFactory;
	private volatile Bm25Index currentIndex;

	public Bm25SearchService(
		AdmissionsRepository repository,
		AppProperties appProperties,
		RetrievalTokenizerFactory tokenizerFactory
	) {
		this.repository = repository;
		this.appProperties = appProperties;
		this.tokenizerFactory = tokenizerFactory;
	}

	public synchronized Bm25IndexMetadataSnapshot rebuild() {
		RetrievalTokenizer tokenizer = tokenizerFactory.current();
		List<Bm25Chunk> chunks = repository.loadChunksForBm25(appProperties.getIndexVersion());
		Bm25Index index = buildIndex(chunks, tokenizer);
		currentIndex = index;
		repository.insertBm25IndexMetadata(
			appProperties.getCorpusProfile(),
			appProperties.getIndexVersion(),
			tokenizer.name(),
			index.documentCount(),
			index.chunkCount(),
			index.corpusHash()
		);
		return new Bm25IndexMetadataSnapshot(
			tokenizer.name(),
			index.documentCount(),
			index.chunkCount(),
			index.corpusHash()
		);
	}

	public List<Bm25SearchResult> search(String query, int limit) {
		Bm25Index index = ensureIndex();
		Map<String, Integer> queryTerms = termFrequency(index.tokenizer().tokenize(query));
		if (queryTerms.isEmpty()) {
			return List.of();
		}

		List<ScoredChunk> scored = new ArrayList<>();
		for (IndexedChunk chunk : index.chunks()) {
			double score = score(queryTerms.keySet(), chunk, index);
			if (score > 0.0d) {
				scored.add(new ScoredChunk(chunk.source(), score));
			}
		}

		List<ScoredChunk> ranked = scored.stream()
			.sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
			.limit(Math.max(1, limit))
			.toList();

		List<Bm25SearchResult> results = new ArrayList<>();
		for (int indexNumber = 0; indexNumber < ranked.size(); indexNumber++) {
			ScoredChunk scoredChunk = ranked.get(indexNumber);
			Bm25Chunk chunk = scoredChunk.chunk();
			results.add(new Bm25SearchResult(
				chunk.chunkId(),
				chunk.documentId(),
				chunk.title(),
				chunk.url(),
				chunk.postedAt(),
				Math.round(scoredChunk.score() * 1_000_000.0d) / 1_000_000.0d,
				indexNumber + 1
			));
		}
		return results;
	}

	public String currentTokenizerName() {
		return ensureIndex().tokenizer().name();
	}

	private Bm25Index ensureIndex() {
		Bm25Index index = currentIndex;
		if (index == null || !appProperties.getIndexVersion().equals(index.indexVersion())) {
			rebuild();
			index = currentIndex;
		}
		return index;
	}

	private Bm25Index buildIndex(List<Bm25Chunk> chunks, RetrievalTokenizer tokenizer) {
		List<IndexedChunk> indexedChunks = new ArrayList<>();
		Map<String, Integer> documentFrequency = new HashMap<>();
		int totalLength = 0;
		for (Bm25Chunk chunk : chunks) {
			Map<String, Integer> termFrequency = termFrequency(tokenizer.tokenize(chunk.content()));
			int length = termFrequency.values().stream().mapToInt(Integer::intValue).sum();
			totalLength += length;
			Set<String> uniqueTerms = new HashSet<>(termFrequency.keySet());
			for (String term : uniqueTerms) {
				documentFrequency.merge(term, 1, Integer::sum);
			}
			indexedChunks.add(new IndexedChunk(chunk, termFrequency, length));
		}
		double averageLength = indexedChunks.isEmpty() ? 0.0d : (double) totalLength / indexedChunks.size();
		String corpusHash = CorpusHashUtils.sha256(chunks.stream()
			.flatMap(chunk -> List.of(
				String.valueOf(chunk.chunkId()),
				String.valueOf(chunk.documentId()),
				chunk.title(),
				chunk.content()
			).stream())
			.toList());
		return new Bm25Index(
			appProperties.getIndexVersion(),
			tokenizer,
			indexedChunks,
			documentFrequency,
			averageLength,
			chunks.stream().map(Bm25Chunk::documentId).collect(java.util.stream.Collectors.toSet()).size(),
			indexedChunks.size(),
			corpusHash
		);
	}

	private double score(Set<String> queryTerms, IndexedChunk chunk, Bm25Index index) {
		if (chunk.length() == 0 || index.averageDocumentLength() == 0.0d) {
			return 0.0d;
		}
		double score = 0.0d;
		for (String term : queryTerms) {
			int tf = chunk.termFrequency().getOrDefault(term, 0);
			if (tf == 0) {
				continue;
			}
			int df = index.documentFrequency().getOrDefault(term, 0);
			double idf = Math.log(1.0d + (index.chunkCount() - df + 0.5d) / (df + 0.5d));
			double denominator = tf + K1 * (1.0d - B + B * chunk.length() / index.averageDocumentLength());
			score += idf * (tf * (K1 + 1.0d)) / denominator;
		}
		return score;
	}

	private Map<String, Integer> termFrequency(List<String> tokens) {
		Map<String, Integer> result = new LinkedHashMap<>();
		for (String token : tokens) {
			result.merge(token, 1, Integer::sum);
		}
		return result;
	}

	public record Bm25IndexMetadataSnapshot(
		String tokenizer,
		int documentCount,
		int chunkCount,
		String corpusHash
	) {
	}

	private record Bm25Index(
		String indexVersion,
		RetrievalTokenizer tokenizer,
		List<IndexedChunk> chunks,
		Map<String, Integer> documentFrequency,
		double averageDocumentLength,
		int documentCount,
		int chunkCount,
		String corpusHash
	) {
	}

	private record IndexedChunk(
		Bm25Chunk source,
		Map<String, Integer> termFrequency,
		int length
	) {
	}

	private record ScoredChunk(Bm25Chunk chunk, double score) {
	}
}
