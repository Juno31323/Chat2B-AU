package com.chat2b.admissions.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ExperimentJsonlWriter {

	private final ObjectMapper objectMapper;

	public ExperimentJsonlWriter() {
		this(new ObjectMapper().configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), false));
	}

	public ExperimentJsonlWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(Path path, List<?> records) {
		try {
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (BufferedWriter writer = Files.newBufferedWriter(
				path,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE
			)) {
				for (Object record : records) {
					writer.write(objectMapper.writeValueAsString(record));
					writer.newLine();
				}
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to write UTF-8 JSONL experiment records.", exception);
		}
	}

	public List<JsonNode> read(Path path) {
		List<JsonNode> records = new ArrayList<>();
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isBlank()) {
					records.add(objectMapper.reader().with(JsonParser.Feature.AUTO_CLOSE_SOURCE).readTree(line));
				}
			}
			return records;
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to read UTF-8 JSONL experiment records.", exception);
		}
	}
}
