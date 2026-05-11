package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DenseVectorSchemaService {

	private static final Logger log = LoggerFactory.getLogger(DenseVectorSchemaService.class);

	private final JdbcTemplate jdbcTemplate;
	private final AppProperties appProperties;
	private volatile boolean pgvectorAvailable;
	private volatile String retrievalMode = "in-memory-cosine";

	public DenseVectorSchemaService(JdbcTemplate jdbcTemplate, AppProperties appProperties) {
		this.jdbcTemplate = jdbcTemplate;
		this.appProperties = appProperties;
	}

	@PostConstruct
	public void initialize() {
		if (!isPostgres() || !appProperties.isPgvectorEnabled()) {
			log.info("Dense retrieval mode: {}.", retrievalMode);
			return;
		}
		try {
			validatePgvectorExperimentSchema();
			pgvectorAvailable = true;
			retrievalMode = "pgvector-cosine";
		} catch (RuntimeException exception) {
			pgvectorAvailable = false;
			retrievalMode = "in-memory-cosine";
			log.warn("pgvector experiment schema is not ready. Falling back to in-memory cosine retrieval. Cause: {}", exception.getMessage());
		}
		log.info("Dense retrieval mode: {}.", retrievalMode);
	}

	public boolean isPgvectorAvailable() {
		return pgvectorAvailable;
	}

	public String retrievalMode() {
		return retrievalMode;
	}

	private boolean isPostgres() {
		return jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
			connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql")
		);
	}

	private void validatePgvectorExperimentSchema() {
		int dimensions = appProperties.getEmbeddingDimensions();
		jdbcTemplate.execute("create extension if not exists vector");
		Integer tableCount = jdbcTemplate.queryForObject(
			"""
			select count(*)
			from information_schema.tables
			where table_schema = current_schema()
			  and table_name in ('documents', 'chunks', 'chunk_embeddings')
			""",
			Integer.class
		);
		if (tableCount == null || tableCount < 3) {
			throw new IllegalStateException("Run V1__create_rag_experiment_schema.sql before starting the experiment profile.");
		}
		String vectorType = jdbcTemplate.queryForObject(
			"""
			select format_type(attribute.atttypid, attribute.atttypmod)
			from pg_attribute attribute
			where attribute.attrelid = 'chunk_embeddings'::regclass
			  and attribute.attname = 'embedding'
			  and not attribute.attisdropped
			""",
			String.class
		);
		String expected = "vector(" + dimensions + ")";
		if (!expected.equalsIgnoreCase(vectorType)) {
			throw new IllegalStateException(
				"chunk_embeddings.embedding dimension mismatch. expected=%s actual=%s. Recreate the experiment schema or reindex with the configured embedding dimension."
					.formatted(expected, vectorType)
			);
		}
		if (appProperties.isPgvectorIndexEnabled()) {
			try {
				jdbcTemplate.execute(
					"create index if not exists idx_chunk_embeddings_embedding_cosine " +
					"on chunk_embeddings using hnsw (embedding vector_cosine_ops)"
				);
			} catch (RuntimeException exception) {
				log.warn("pgvector column is available, but vector index creation failed. Sequential pgvector search will be used. Cause: {}", exception.getMessage());
			}
		}
	}
}
