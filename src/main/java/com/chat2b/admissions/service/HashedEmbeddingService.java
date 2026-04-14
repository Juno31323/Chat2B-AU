package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.support.TextTokenUtils;
import org.springframework.stereotype.Service;

@Service
public class HashedEmbeddingService {

	private final AppProperties appProperties;

	public HashedEmbeddingService(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	public float[] embed(String text) {
		int dimension = appProperties.getEmbeddingDimensions();
		float[] vector = new float[dimension];
		for (String token : TextTokenUtils.tokenize(text)) {
			int index = Math.floorMod(token.hashCode(), dimension);
			vector[index] += 1.0f;
		}
		normalize(vector);
		return vector;
	}

	private void normalize(float[] vector) {
		double norm = 0.0d;
		for (float value : vector) {
			norm += value * value;
		}
		if (norm == 0.0d) {
			return;
		}
		float scale = (float) (1.0d / Math.sqrt(norm));
		for (int i = 0; i < vector.length; i++) {
			vector[i] = vector[i] * scale;
		}
	}
}
