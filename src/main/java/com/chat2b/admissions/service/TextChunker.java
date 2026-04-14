package com.chat2b.admissions.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class TextChunker {

	private static final int TARGET_CHUNK_SIZE = 700;
	private static final int MIN_CHUNK_SIZE = 180;
	private static final int HARD_SPLIT_SIZE = 620;
	private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n");

	public List<ChunkCandidate> chunkSections(List<SectionText> sections) {
		List<ChunkCandidate> chunks = new ArrayList<>();
		for (SectionText section : sections) {
			String normalized = normalize(section.text());
			if (!StringUtils.hasText(normalized)) {
				continue;
			}
			chunks.addAll(chunkSingleSection(section.sectionName(), section.pageNumber(), normalized));
		}
		return chunks;
	}

	private List<ChunkCandidate> chunkSingleSection(String sectionName, Integer pageNumber, String text) {
		List<ChunkCandidate> chunks = new ArrayList<>();
		List<String> paragraphs = Arrays.stream(BLANK_LINE.split(text))
			.map(this::normalize)
			.filter(StringUtils::hasText)
			.toList();
		if (paragraphs.isEmpty()) {
			paragraphs = List.of(text);
		}

		StringBuilder current = new StringBuilder();
		for (String paragraph : paragraphs) {
			if (paragraph.length() > TARGET_CHUNK_SIZE) {
				flush(sectionName, pageNumber, current, chunks);
				splitLongParagraph(sectionName, pageNumber, paragraph, chunks);
				continue;
			}
			if (current.length() + paragraph.length() + 2 > TARGET_CHUNK_SIZE && current.length() >= MIN_CHUNK_SIZE) {
				flush(sectionName, pageNumber, current, chunks);
			}
			if (!current.isEmpty()) {
				current.append("\n\n");
			}
			current.append(paragraph);
		}
		flush(sectionName, pageNumber, current, chunks);
		return chunks;
	}

	private void splitLongParagraph(String sectionName, Integer pageNumber, String paragraph, List<ChunkCandidate> chunks) {
		int start = 0;
		while (start < paragraph.length()) {
			int end = Math.min(start + HARD_SPLIT_SIZE, paragraph.length());
			chunks.add(new ChunkCandidate(sectionName, pageNumber, paragraph.substring(start, end).trim()));
			start = end;
		}
	}

	private void flush(String sectionName, Integer pageNumber, StringBuilder current, List<ChunkCandidate> chunks) {
		String content = normalize(current.toString());
		if (StringUtils.hasText(content)) {
			chunks.add(new ChunkCandidate(sectionName, pageNumber, content));
		}
		current.setLength(0);
	}

	private String normalize(String value) {
		String normalized = value.replace("\r", "");
		normalized = normalized.replaceAll("[ \\t]+", " ");
		normalized = normalized.replaceAll("\\n{3,}", "\n\n");
		return normalized.trim();
	}

	public record SectionText(String sectionName, Integer pageNumber, String text) {
	}

	public record ChunkCandidate(String sectionName, Integer pageNumber, String content) {
	}
}
