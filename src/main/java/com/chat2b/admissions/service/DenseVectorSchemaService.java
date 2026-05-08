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
	private volatile boolean postgres;
	private volatile boolean pgvectorAvailable;
	private volatile String retrievalMode = "in-memory-cosine";

	public DenseVectorSchemaService(JdbcTemplate jdbcTemplate, AppProperties appProperties) {
		this.jdbcTemplate = jdbcTemplate;
		this.appProperties = appProperties;
	}

	@PostConstruct
	public void initialize() {
		postgres = isPostgres();
		ensureCommonDenseColumns();
		if (!postgres || !appProperties.isPgvectorEnabled()) {
			log.info("Dense retrieval mode: {}.", retrievalMode);
			return;
		}
		try {
			enablePgvector();
			pgvectorAvailable = true;
			retrievalMode = "pgvector-cosine";
		} catch (RuntimeException exception) {
			pgvectorAvailable = false;
			retrievalMode = "in-memory-cosine";
			log.warn("pgvector setup failed. Falling back to in-memory cosine retrieval. Cause: {}", exception.getMessage());
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

	private void ensureCommonDenseColumns() {
		jdbcTemplate.execute("alter table documents add column if not exists index_name varchar(120)");
		jdbcTemplate.execute("alter table documents add column if not exists corpus_profile varchar(50)");
		jdbcTemplate.execute("alter table documents add column if not exists index_version varchar(100)");
		jdbcTemplate.execute("alter table documents add column if not exists url varchar(1024)");
		jdbcTemplate.execute("alter table documents add column if not exists posted_at timestamp");
		jdbcTemplate.execute("alter table document_chunks add column if not exists embedding_model varchar(120)");
		jdbcTemplate.execute("alter table document_chunks add column if not exists embedding_dim integer");
		jdbcTemplate.execute("alter table document_chunks add column if not exists index_version varchar(100)");
		jdbcTemplate.execute("alter table index_metadata add column if not exists index_name varchar(120)");
		jdbcTemplate.execute("alter table index_metadata add column if not exists embedding_dim integer");
		jdbcTemplate.execute("alter table index_metadata add column if not exists retrieval_config_hash varchar(64)");
		jdbcTemplate.execute("alter table index_metadata add column if not exists source_data_path varchar(512)");
		jdbcTemplate.execute("alter table chat_logs add column if not exists generation_provider varchar(50)");
		jdbcTemplate.execute("alter table chat_logs add column if not exists generation_model varchar(120)");
		jdbcTemplate.execute("alter table chat_logs add column if not exists generation_model_version varchar(160)");
		jdbcTemplate.execute("alter table chat_logs add column if not exists generation_temperature double precision");
		jdbcTemplate.execute("alter table chat_logs add column if not exists generation_max_output_tokens integer");
		jdbcTemplate.execute("alter table chat_logs add column if not exists prompt_version varchar(120)");
		jdbcTemplate.execute("alter table chat_logs add column if not exists input_tokens integer");
		jdbcTemplate.execute("alter table chat_logs add column if not exists output_tokens integer");
		jdbcTemplate.execute("alter table chat_logs add column if not exists total_tokens integer");
		jdbcTemplate.execute("alter table chat_logs add column if not exists estimated_cost_usd double precision");
	}

	private void enablePgvector() {
		int dimensions = appProperties.getEmbeddingDimensions();
		jdbcTemplate.execute("create extension if not exists vector");
		jdbcTemplate.execute("alter table document_chunks add column if not exists embedding_vector vector(" + dimensions + ")");
		validatePgvectorDimensions(dimensions);
		if (appProperties.isPgvectorIndexEnabled()) {
			try {
				jdbcTemplate.execute(
					"create index if not exists idx_document_chunks_embedding_vector_cosine " +
					"on document_chunks using hnsw (embedding_vector vector_cosine_ops)"
				);
			} catch (RuntimeException exception) {
				log.warn("pgvector column is available, but vector index creation failed. Sequential pgvector search will be used. Cause: {}", exception.getMessage());
			}
		}
	}

	private void validatePgvectorDimensions(int dimensions) {
		String vectorType = jdbcTemplate.queryForObject(
			"""
			select format_type(attribute.atttypid, attribute.atttypmod)
			from pg_attribute attribute
			where attribute.attrelid = 'document_chunks'::regclass
			  and attribute.attname = 'embedding_vector'
			  and not attribute.attisdropped
			""",
			String.class
		);
		String expected = "vector(" + dimensions + ")";
		if (!expected.equalsIgnoreCase(vectorType)) {
			throw new IllegalStateException(
				"embedding_vector dimension mismatch. expected=%s actual=%s. Recreate the vector column or reindex with the configured embedding dimension."
					.formatted(expected, vectorType)
			);
		}
	}
}
