package com.chat2b.admissions.service.generation;

import com.chat2b.admissions.config.GenerationProperties;
import org.springframework.stereotype.Component;

@Component
public class GenerationCostEstimator {

	private final GenerationProperties properties;

	public GenerationCostEstimator(GenerationProperties properties) {
		this.properties = properties;
	}

	public Double estimateUsd(Integer inputTokens, Integer outputTokens) {
		return estimateUsd(inputTokens, outputTokens, false);
	}

	public Double estimateUsd(Integer inputTokens, Integer outputTokens, boolean important) {
		if (inputTokens == null || outputTokens == null) {
			return null;
		}
		double inputRate = important && properties.getImportantInputCostPerMillionTokensUsd() > 0.0d
			? properties.getImportantInputCostPerMillionTokensUsd()
			: properties.getInputCostPerMillionTokensUsd();
		double outputRate = important && properties.getImportantOutputCostPerMillionTokensUsd() > 0.0d
			? properties.getImportantOutputCostPerMillionTokensUsd()
			: properties.getOutputCostPerMillionTokensUsd();
		double inputCost = inputTokens / 1_000_000.0d * inputRate;
		double outputCost = outputTokens / 1_000_000.0d * outputRate;
		return Math.round((inputCost + outputCost) * 1_000_000_000.0d) / 1_000_000_000.0d;
	}
}
