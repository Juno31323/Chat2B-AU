package com.chat2b.admissions.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class DataSourceConfig {

	@Bean
	public DataSource dataSource(Environment environment) {
		String rawUrl = firstNonBlank(
			environment.getProperty("DATABASE_URL"),
			environment.getProperty("spring.datasource.url"),
			"jdbc:h2:file:./.data/admissions_chatbot;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
		);
		String username = firstNonBlank(
			environment.getProperty("DATABASE_USERNAME"),
			environment.getProperty("spring.datasource.username"),
			"sa"
		);
		String password = firstNonBlank(
			environment.getProperty("DATABASE_PASSWORD"),
			environment.getProperty("spring.datasource.password"),
			""
		);
		String driverClassName = firstNonBlank(
			environment.getProperty("DATABASE_DRIVER_CLASS_NAME"),
			environment.getProperty("spring.datasource.driver-class-name"),
			resolveDriverClassName(rawUrl)
		);

		ResolvedDataSource resolved = normalize(rawUrl, username, password, driverClassName);

		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl(resolved.url());
		dataSource.setUsername(resolved.username());
		dataSource.setPassword(resolved.password());
		if (StringUtils.hasText(resolved.driverClassName())) {
			dataSource.setDriverClassName(resolved.driverClassName());
		}
		return dataSource;
	}

	static ResolvedDataSource normalize(String rawUrl, String username, String password, String driverClassName) {
		if (!StringUtils.hasText(rawUrl)) {
			throw new IllegalStateException("DATABASE_URL or spring.datasource.url must be configured.");
		}

		if (rawUrl.startsWith("jdbc:")) {
			return new ResolvedDataSource(rawUrl, username, password, driverClassName);
		}

		if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
			return fromPostgresUri(rawUrl, username, password);
		}

		return new ResolvedDataSource(rawUrl, username, password, driverClassName);
	}

	private static ResolvedDataSource fromPostgresUri(String rawUrl, String username, String password) {
		try {
			String normalizedUrl = rawUrl.startsWith("postgres://")
				? "postgresql://" + rawUrl.substring("postgres://".length())
				: rawUrl;
			URI uri = new URI(normalizedUrl);
			StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
			if (uri.getPort() > 0) {
				jdbcUrl.append(":").append(uri.getPort());
			}
			jdbcUrl.append(uri.getPath());
			if (StringUtils.hasText(uri.getQuery())) {
				jdbcUrl.append("?").append(uri.getQuery());
			}

			String resolvedUsername = username;
			String resolvedPassword = password;
			if (StringUtils.hasText(uri.getUserInfo())) {
				String[] parts = uri.getUserInfo().split(":", 2);
				if (!StringUtils.hasText(resolvedUsername) && parts.length > 0) {
					resolvedUsername = decode(parts[0]);
				}
				if (!StringUtils.hasText(resolvedPassword) && parts.length > 1) {
					resolvedPassword = decode(parts[1]);
				}
			}

			return new ResolvedDataSource(
				jdbcUrl.toString(),
				firstNonBlank(resolvedUsername, ""),
				firstNonBlank(resolvedPassword, ""),
				"org.postgresql.Driver"
			);
		} catch (URISyntaxException exception) {
			throw new IllegalStateException("DATABASE_URL is not a valid PostgreSQL connection string.", exception);
		}
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		return "";
	}

	private static String resolveDriverClassName(String rawUrl) {
		if (rawUrl != null && (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://") || rawUrl.startsWith("jdbc:postgresql:"))) {
			return "org.postgresql.Driver";
		}
		return "org.h2.Driver";
	}

	record ResolvedDataSource(String url, String username, String password, String driverClassName) {
	}
}
