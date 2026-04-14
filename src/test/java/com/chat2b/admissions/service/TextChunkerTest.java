package com.chat2b.admissions.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

	@Test
	void keepsSectionMetadataWhileChunking() {
		TextChunker chunker = new TextChunker();
		List<TextChunker.ChunkCandidate> chunks = chunker.chunkSections(List.of(
			new TextChunker.SectionText(
				"수시 1차",
				null,
				"""
				원서접수는 2025년 9월 8일부터 9월 30일 오후 6시까지입니다.

				서류제출은 2025년 10월 2일 오후 5시까지 도착분에 한합니다.
				면접은 간호학과, 항공서비스과 지원자에게만 실시합니다.
				"""
			)
		));

		assertFalse(chunks.isEmpty());
		assertTrue(chunks.stream().allMatch(chunk -> "수시 1차".equals(chunk.sectionName())));
	}
}
