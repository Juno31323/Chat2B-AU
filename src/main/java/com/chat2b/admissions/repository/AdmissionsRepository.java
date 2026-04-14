package com.chat2b.admissions.repository;

import com.chat2b.admissions.model.RetrievedChunk;
import com.chat2b.admissions.model.SourceReference;
import com.chat2b.admissions.support.VectorUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AdmissionsRepository {

	private final JdbcTemplate jdbcTemplate;

	public AdmissionsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void clearKnowledgeBase() {
		jdbcTemplate.update("delete from chat_logs");
		jdbcTemplate.update("delete from documents");
	}

	public long insertDocument(String title, String sourcePath, String contentType) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(
				"insert into documents (title, source_path, content_type) values (?, ?, ?)",
				new String[]{"id"}
			);
			statement.setString(1, title);
			statement.setString(2, sourcePath);
			statement.setString(3, contentType);
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
		float[] embedding
	) {
		jdbcTemplate.update(
			"""
			insert into document_chunks
			    (document_id, chunk_index, content, page_number, section_name, embedding)
			values (?, ?, ?, ?, ?, ?)
			""",
			documentId,
			chunkIndex,
			content,
			pageNumber,
			sectionName,
			VectorUtils.toPgVectorLiteral(embedding)
		);
	}

	public List<RetrievedChunk> searchSimilarChunks(float[] embedding, int limit) {
		List<RetrievedChunk> allChunks = jdbcTemplate.query(
			"""
			select
			    c.id,
			    c.document_id,
			    d.title,
			    c.content,
			    c.page_number,
			    c.section_name,
			    c.embedding
			from document_chunks c
			join documents d on d.id = c.document_id
			""",
			(rs, rowNum) -> new RetrievedChunk(
				rs.getLong("id"),
				rs.getLong("document_id"),
				rs.getString("title"),
				rs.getString("content"),
				(Integer) rs.getObject("page_number"),
				rs.getString("section_name"),
				VectorUtils.cosineSimilarity(embedding, VectorUtils.fromPgVectorLiteral(rs.getString("embedding")))
			)
		);
		return allChunks.stream()
			.sorted(Comparator.comparingDouble(RetrievedChunk::similarity).reversed())
			.limit(limit)
			.toList();
	}

	public void logChat(
		String question,
		String answer,
		String answerMode,
		String retrievalStatus,
		List<SourceReference> sources,
		String ipAddress,
		String sessionId
	) {
		jdbcTemplate.update(
			"""
			insert into chat_logs
			    (question, answer, answer_mode, retrieval_status, source_labels, ip_address, session_id)
			values (?, ?, ?, ?, ?, ?, ?)
			""",
			question,
			answer,
			answerMode,
			retrievalStatus,
			sources.stream().map(SourceReference::label).reduce((left, right) -> left + " | " + right).orElse(""),
			ipAddress,
			sessionId
		);
	}

	public Map<String, Object> getStatusSummary() {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("documents", jdbcTemplate.queryForObject("select count(*) from documents", Long.class));
		summary.put("chunks", jdbcTemplate.queryForObject("select count(*) from document_chunks", Long.class));
		summary.put("chatLogs", jdbcTemplate.queryForObject("select count(*) from chat_logs", Long.class));
		return summary;
	}

	public boolean hasKnowledgeBase() {
		Long documentCount = jdbcTemplate.queryForObject("select count(*) from documents", Long.class);
		Long chunkCount = jdbcTemplate.queryForObject("select count(*) from document_chunks", Long.class);
		return documentCount != null && chunkCount != null && documentCount > 0 && chunkCount > 0;
	}
}
