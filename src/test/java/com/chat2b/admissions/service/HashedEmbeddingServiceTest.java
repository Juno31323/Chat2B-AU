package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashedEmbeddingServiceTest {

	@Test
	void embedsIntoConfiguredDimension() {
		AppProperties properties = new AppProperties();
		properties.setEmbeddingDimensions(64);

		HashedEmbeddingService service = new HashedEmbeddingService(properties);
		float[] vector = service.embed("2026학년도 수시 1차 원서접수 일정이 궁금합니다.");

		assertEquals(64, vector.length);
		assertTrue(magnitude(vector) > 0.0d);
	}

	private double magnitude(float[] vector) {
		double magnitude = 0.0d;
		for (float value : vector) {
			magnitude += value * value;
		}
		return Math.sqrt(magnitude);
	}
}
