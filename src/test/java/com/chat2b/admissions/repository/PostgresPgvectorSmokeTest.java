package com.chat2b.admissions.repository;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresPgvectorSmokeTest {

	@Test
	void pgvectorExperimentDatabaseStoresKoreanAndSearchesByCosine() throws Exception {
		String jdbcUrl = firstNonBlank(
			System.getenv("EXPERIMENT_DATABASE_URL"),
			System.getenv("SUPABASE_DB_URL"),
			System.getenv("DATABASE_URL")
		);
		Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank(), "PostgreSQL smoke DB URL is not configured.");
		jdbcUrl = normalizeJdbcUrl(jdbcUrl);
		Assumptions.assumeTrue(jdbcUrl.startsWith("jdbc:postgresql:"), "Smoke test requires PostgreSQL.");

		String username = firstNonBlank(System.getenv("SUPABASE_DB_USER"), System.getenv("DATABASE_USERNAME"));
		String password = firstNonBlank(System.getenv("SUPABASE_DB_PASSWORD"), System.getenv("DATABASE_PASSWORD"));

		try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			 Statement statement = connection.createStatement()) {
			statement.execute("create extension if not exists vector");
			statement.execute("drop table if exists pgvector_smoke_chunks");
			statement.execute("create table pgvector_smoke_chunks (id bigserial primary key, title text not null, content text not null, embedding vector(3) not null)");
			statement.execute("insert into pgvector_smoke_chunks (title, content, embedding) values ('한글 공지', '수시 1차 원서접수 안내입니다.', '[1,0,0]')");
			statement.execute("insert into pgvector_smoke_chunks (title, content, embedding) values ('장학금 공지', '신입생 장학금 안내입니다.', '[0,1,0]')");

			try (ResultSet resultSet = statement.executeQuery(
				"select title, content, 1 - (embedding <=> '[1,0,0]'::vector) as score " +
				"from pgvector_smoke_chunks order by embedding <=> '[1,0,0]'::vector limit 1"
			)) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getString("title")).isEqualTo("한글 공지");
				assertThat(resultSet.getString("content")).contains("수시 1차");
				assertThat(resultSet.getDouble("score")).isGreaterThan(0.99d);
			}
		}
	}

	private static String normalizeJdbcUrl(String value) {
		if (value.startsWith("postgres://")) {
			return "jdbc:postgresql://" + value.substring("postgres://".length());
		}
		if (value.startsWith("postgresql://")) {
			return "jdbc:postgresql://" + value.substring("postgresql://".length());
		}
		return value;
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}
}
