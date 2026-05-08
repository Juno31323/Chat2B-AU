package com.chat2b.admissions.repository;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.model.RetrievedChunk;
import com.chat2b.admissions.model.Bm25Chunk;
import com.chat2b.admissions.model.Bm25IndexMetadata;
import com.chat2b.admissions.model.GenerationMetadata;
import com.chat2b.admissions.model.HybridSearchResult;
import com.chat2b.admissions.model.IndexMetadata;
import com.chat2b.admissions.model.SourceReference;
import com.chat2b.admissions.service.DenseVectorSchemaService;
import com.chat2b.admissions.support.VectorUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AdmissionsRepository {

	private final JdbcTemplate jdbcTemplate;
	private final DenseVectorSchemaService denseVectorSchemaService;
	private final AppProperties appProperties;

	public AdmissionsRepository(JdbcTemplate jdbcTemplate, DenseVectorSchemaService denseVectorSchemaService, AppProperties appProperties) {
		this.jdbcTemplate = jdbcTemplate;
		this.denseVectorSchemaService = denseVectorSchemaService;
		this.appProperties = appProperties;
	}

	public void clearKnowledgeBase() {
		jdbcTemplate.update(
			"delete from documents where index_name = ? and index_version = ?",
			appProperties.getIndexName(),
			appProperties.getIndexVersion()
		);
	}

	public long insertDocument(String title, String sourcePath, String contentType) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(
				"insert into documents (index_name, corpus_profile, index_version, title, source_path, content_type) values (?, ?, ?, ?, ?, ?)",
				new String[]{"id"}
			);
			statement.setString(1, appProperties.getIndexName());
			statement.setString(2, appProperties.getCorpusProfile());
			statement.setString(3, appProperties.getIndexVersion());
			statement.setString(4, title);
			statement.setString(5, sourcePath);
			statement.setString(6, contentType);
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Failed to insert document.");
		}
		return key.longValue();
	}

	public void insertChunk(
		long documentId,
		int chunkIndex,
		String content,
		Integer pageNumber,
		String sectionName,
		float[] embedding,
		String embeddingModel,
		int embeddingDim,
		String indexVersion
	) {
		String vectorLiteral = VectorUtils.toPgVectorLiteral(embedding);
		if (denseVectorSchemaService.isPgvectorAvailable()) {
			jdbcTemplate.update(
				"""
				insert into document_chunks
				    (document_id, chunk_index, content, page_number, section_name, embedding, embedding_model, embedding_dim, index_version, embedding_vector)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
				""",
				documentId,
				chunkIndex,
				content,
				pageNumber,
				sectionName,
				vectorLiteral,
				embeddingModel,
				embeddingDim,
				indexVersion,
				vectorLiteral
			);
			return;
		}
		jdbcTemplate.update(
			"""
			insert into document_chunks
			    (document_id, chunk_index, content, page_number, section_name, embedding, embedding_model, embedding_dim, index_version)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			documentId,
			chunkIndex,
			content,
			pageNumber,
			sectionName,
			vectorLiteral,
			embeddingModel,
			embeddingDim,
			indexVersion
		);
	}

	public List<RetrievedChunk> searchSimilarChunks(float[] embedding, int limit, String embeddingModel, int embeddingDim, String indexVersion) {
		if (denseVectorSchemaService.isPgvectorAvailable()) {
			return searchSimilarChunksWithPgvector(embedding, limit, embeddingModel, embeddingDim, indexVersion);
		}
		return searchSimilarChunksInMemory(embedding, limit, embeddingModel, embeddingDim, indexVersion);
	}

	private List<RetrievedChunk> searchSimilarChunksWithPgvector(float[] embedding, int limit, String embeddingModel, int embeddingDim, String indexVersion) {
		String vectorLiteral = VectorUtils.toPgVectorLiteral(embedding);
		return jdbcTemplate.query(
			"""
			select
			    c.id,
			    c.document_id,
			    d.title,
			    d.url,
			    d.posted_at,
			    c.content,
			    c.page_number,
			    c.section_name,
			    1 - (c.embedding_vector <=> ?::vector) as dense_score,
			    row_number() over (order by c.embedding_vector <=> ?::vector) as dense_rank
			from document_chunks c
			join documents d on d.id = c.document_id
			where c.embedding_vector is not null
			  and c.embedding_model = ?
			  and c.embedding_dim = ?
			  and c.index_version = ?
			  and d.index_name = ?
			  and d.index_version = ?
			order by c.embedding_vector <=> ?::vector
			limit ?
			""",
			(rs, rowNum) -> new RetrievedChunk(
				rs.getLong("id"),
				rs.getLong("document_id"),
				rs.getString("title"),
				rs.getString("url"),
				toInstant(rs.getTimestamp("posted_at")),
				rs.getString("content"),
				(Integer) rs.getObject("page_number"),
				rs.getString("section_name"),
				rs.getDouble("dense_score"),
				rs.getInt("dense_rank")
			),
			vectorLiteral,
			vectorLiteral,
			embeddingModel,
			embeddingDim,
			indexVersion,
			appProperties.getIndexName(),
			indexVersion,
			vectorLiteral,
			limit
		);
	}

	private List<RetrievedChunk> searchSimilarChunksInMemory(float[] embedding, int limit, String embeddingModel, int embeddingDim, String indexVersion) {
		List<RetrievedChunk> allChunks = jdbcTemplate.query(
			"""
			select
			    c.id,
			    c.document_id,
			    d.title,
			    d.url,
			    d.posted_at,
			    c.content,
			    c.page_number,
			    c.section_name,
			    c.embedding
			from document_chunks c
			join documents d on d.id = c.document_id
			where c.embedding_model = ?
			  and c.embedding_dim = ?
			  and c.index_version = ?
			  and d.index_name = ?
			  and d.index_version = ?
			""",
			(rs, rowNum) -> new RetrievedChunk(
				rs.getLong("id"),
				rs.getLong("document_id"),
				rs.getString("title"),
				rs.getString("url"),
				toInstant(rs.getTimestamp("posted_at")),
				rs.getString("content"),
				(Integer) rs.getObject("page_number"),
				rs.getString("section_name"),
				VectorUtils.cosineSimilarity(embedding, VectorUtils.fromPgVectorLiteral(rs.getString("embedding"))),
				0
			),
			embeddingModel,
			embeddingDim,
			indexVersion,
			appProperties.getIndexName(),
			indexVersion
		);
		List<RetrievedChunk> ranked = new ArrayList<>(allChunks.stream()
			.sorted(Comparator.comparingDouble(RetrievedChunk::similarity).reversed())
			.limit(limit)
			.toList());
		for (int index = 0; index < ranked.size(); index++) {
			RetrievedChunk chunk = ranked.get(index);
			ranked.set(index, new RetrievedChunk(
				chunk.chunkId(),
				chunk.documentId(),
				chunk.documentTitle(),
				chunk.url(),
				chunk.postedAt(),
				chunk.content(),
				chunk.pageNumber(),
				chunk.sectionName(),
				chunk.denseScore(),
				index + 1
			));
		}
		return ranked;
	}

	public void logChat(
		String question,
		String answer,
		String answerMode,
		String retrievalStatus,
		List<SourceReference> sources,
		GenerationMetadata generationMetadata,
		String ipAddress,
		String sessionId
	) {
		jdbcTemplate.update(
			"""
			insert into chat_logs
			    (question, answer, answer_mode, retrieval_status, source_labels,
			     generation_provider, generation_model, generation_model_version, generation_temperature,
			     generation_max_output_tokens, prompt_version, input_tokens, output_tokens, total_tokens,
			     estimated_cost_usd, ip_address, session_id)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			question,
			answer,
			answerMode,
			retrievalStatus,
			sources.stream().map(SourceReference::label).reduce((left, right) -> left + " | " + right).orElse(""),
			generationMetadata == null ? null : generationMetadata.provider(),
			generationMetadata == null ? null : generationMetadata.model(),
			generationMetadata == null ? null : generationMetadata.modelVersion(),
			generationMetadata == null ? null : generationMetadata.temperature(),
			generationMetadata == null ? null : generationMetadata.maxOutputTokens(),
			generationMetadata == null ? null : generationMetadata.promptVersion(),
			generationMetadata == null ? null : generationMetadata.inputTokens(),
			generationMetadata == null ? null : generationMetadata.outputTokens(),
			generationMetadata == null ? null : generationMetadata.totalTokens(),
			generationMetadata == null ? null : generationMetadata.estimatedCostUsd(),
			ipAddress,
			sessionId
		);
	}

	public void insertIndexMetadata(
		String indexName,
		String corpusProfile,
		String indexVersion,
		int documentCount,
		int chunkCount,
		String embeddingModel,
		int embeddingDim,
		int chunkSize,
		int chunkOverlap,
		String tokenizer,
		String corpusHash,
		String retrievalConfigHash,
		String sourceDataPath
	) {
		jdbcTemplate.update(
			"""
			insert into index_metadata
			    (index_name, corpus_profile, index_version, document_count, chunk_count, embedding_model, embedding_dim,
			     chunk_size, chunk_overlap, tokenizer, corpus_hash, retrieval_config_hash, source_data_path)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			indexName,
			corpusProfile,
			indexVersion,
			documentCount,
			chunkCount,
			embeddingModel,
			embeddingDim,
			chunkSize,
			chunkOverlap,
			tokenizer,
			corpusHash,
			retrievalConfigHash,
			sourceDataPath
		);
	}

	public Map<String, Object> getStatusSummary() {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("documents", jdbcTemplate.queryForObject("select count(*) from documents", Long.class));
		summary.put("chunks", jdbcTemplate.queryForObject("select count(*) from document_chunks", Long.class));
		summary.put("chatLogs", jdbcTemplate.queryForObject("select count(*) from chat_logs", Long.class));
		return summary;
	}

	public Optional<IndexMetadata> getLatestIndexMetadata() {
		return getLatestIndexMetadata(null, null);
	}

	public Optional<IndexMetadata> getLatestIndexMetadata(String indexName, String indexVersion) {
		List<Object> args = new ArrayList<>();
		StringBuilder where = new StringBuilder("where 1 = 1");
		if (indexName != null && !indexName.isBlank()) {
			where.append(" and index_name = ?");
			args.add(indexName);
		}
		if (indexVersion != null && !indexVersion.isBlank()) {
			where.append(" and index_version = ?");
			args.add(indexVersion);
		}
		List<IndexMetadata> metadata = jdbcTemplate.query(
			"""
			select
			    id,
			    index_name,
			    corpus_profile,
			    index_version,
			    document_count,
			    chunk_count,
			    embedding_model,
			    embedding_dim,
			    chunk_size,
			    chunk_overlap,
			    tokenizer,
			    corpus_hash,
			    retrieval_config_hash,
			    source_data_path,
			    created_at
			from index_metadata
			""" + where + """
			order by id desc
			limit 1
			""",
			(rs, rowNum) -> new IndexMetadata(
				rs.getLong("id"),
				rs.getString("index_name"),
				rs.getString("corpus_profile"),
				rs.getString("index_version"),
				rs.getInt("document_count"),
				rs.getInt("chunk_count"),
				rs.getString("embedding_model"),
				rs.getInt("embedding_dim"),
				rs.getInt("chunk_size"),
				rs.getInt("chunk_overlap"),
				rs.getString("tokenizer"),
				rs.getString("corpus_hash"),
				rs.getString("retrieval_config_hash"),
				rs.getString("source_data_path"),
				toInstant(rs.getTimestamp("created_at"))
			),
			args.toArray()
		);
		return metadata.stream().findFirst();
	}

	public List<Bm25Chunk> loadChunksForBm25(String indexVersion) {
		return jdbcTemplate.query(
			"""
			select
			    c.id,
			    c.document_id,
			    d.title,
			    d.url,
			    d.posted_at,
			    c.content
			from document_chunks c
			join documents d on d.id = c.document_id
			where c.index_version = ?
			  and d.index_name = ?
			  and d.index_version = ?
			order by c.id
			""",
			(rs, rowNum) -> new Bm25Chunk(
				rs.getLong("id"),
				rs.getLong("document_id"),
				rs.getString("title"),
				rs.getString("url"),
				toInstant(rs.getTimestamp("posted_at")),
				rs.getString("content")
			),
			indexVersion,
			appProperties.getIndexName(),
			indexVersion
		);
	}

	public void insertBm25IndexMetadata(
		String corpusProfile,
		String indexVersion,
		String tokenizer,
		int documentCount,
		int chunkCount,
		String corpusHash
	) {
		jdbcTemplate.update(
			"""
			insert into bm25_index_metadata
			    (corpus_profile, index_version, tokenizer, document_count, chunk_count, corpus_hash)
			values (?, ?, ?, ?, ?, ?)
			""",
			corpusProfile,
			indexVersion,
			tokenizer,
			documentCount,
			chunkCount,
			corpusHash
		);
	}

	public void logHybridRetrieval(
		String question,
		String indexVersion,
		String fusionMethod,
		List<HybridSearchResult> results
	) {
		for (HybridSearchResult result : results) {
			jdbcTemplate.update(
				"""
				insert into retrieval_logs
				    (question, retrieval_method, index_version, fusion_method, chunk_id, document_id, title, url, posted_at,
				     bm25_score, dense_score, hybrid_score, bm25_rank, dense_rank, hybrid_rank)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				question,
				result.retrievalMethod(),
				indexVersion,
				fusionMethod,
				result.chunkId(),
				result.documentId(),
				result.title(),
				result.url(),
				result.postedAt() == null ? null : Timestamp.from(result.postedAt()),
				result.bm25Score(),
				result.denseScore(),
				result.hybridScore(),
				result.bm25Rank(),
				result.denseRank(),
				result.hybridRank()
			);
		}
	}

	public Optional<Bm25IndexMetadata> getLatestBm25IndexMetadata() {
		List<Bm25IndexMetadata> metadata = jdbcTemplate.query(
			"""
			select
			    id,
			    corpus_profile,
			    index_version,
			    tokenizer,
			    document_count,
			    chunk_count,
			    corpus_hash,
			    created_at
			from bm25_index_metadata
			order by id desc
			limit 1
			""",
			(rs, rowNum) -> new Bm25IndexMetadata(
				rs.getLong("id"),
				rs.getString("corpus_profile"),
				rs.getString("index_version"),
				rs.getString("tokenizer"),
				rs.getInt("document_count"),
				rs.getInt("chunk_count"),
				rs.getString("corpus_hash"),
				toInstant(rs.getTimestamp("created_at"))
			)
		);
		return metadata.stream().findFirst();
	}

	public boolean hasKnowledgeBase() {
		Long documentCount = jdbcTemplate.queryForObject(
			"select count(*) from documents where index_name = ? and index_version = ?",
			Long.class,
			appProperties.getIndexName(),
			appProperties.getIndexVersion()
		);
		Long chunkCount = jdbcTemplate.queryForObject(
			"""
			select count(*)
			from document_chunks c
			join documents d on d.id = c.document_id
			where d.index_name = ?
			  and d.index_version = ?
			  and c.index_version = ?
			""",
			Long.class,
			appProperties.getIndexName(),
			appProperties.getIndexVersion(),
			appProperties.getIndexVersion()
		);
		return documentCount != null && chunkCount != null && documentCount > 0 && chunkCount > 0;
	}

	private Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}
}
