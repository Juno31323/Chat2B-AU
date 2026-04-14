package com.chat2b.admissions.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataSourceConfigTest {

	@Test
	void normalizesRenderStylePostgresUrlToJdbc() {
		DataSourceConfig.ResolvedDataSource resolved = DataSourceConfig.normalize(
			"postgres://demo-user:demo-pass@demo-host:5432/demo_db?sslmode=require",
			"",
			"",
			"org.postgresql.Driver"
		);

		assertEquals("jdbc:postgresql://demo-host:5432/demo_db?sslmode=require", resolved.url());
		assertEquals("demo-user", resolved.username());
		assertEquals("demo-pass", resolved.password());
		assertEquals("org.postgresql.Driver", resolved.driverClassName());
	}

	@Test
	void keepsJdbcUrlAsIs() {
		DataSourceConfig.ResolvedDataSource resolved = DataSourceConfig.normalize(
			"jdbc:postgresql://demo-host:5432/demo_db",
			"demo-user",
			"demo-pass",
			"org.postgresql.Driver"
		);

		assertEquals("jdbc:postgresql://demo-host:5432/demo_db", resolved.url());
		assertEquals("demo-user", resolved.username());
		assertEquals("demo-pass", resolved.password());
	}
}
