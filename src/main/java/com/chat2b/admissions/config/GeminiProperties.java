package com.chat2b.admissions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

	private String apiKey;
	private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
	private String chatModel = "gemini-2.5-flash-lite";
	private String embeddingModel = "gemini-embedding-001";
	private int embeddingDimensions = 256;
	private double temperature = 0.1d;
	private int maxOutputTokens = 350;

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getChatModel() {
		return chatModel;
	}

	public void setChatModel(String chatModel) {
		this.chatModel = chatModel;
	}

	public String getEmbeddingModel() {
		return embeddingModel;
	}

	public void setEmbeddingModel(String embeddingModel) {
		this.embeddingModel = embeddingModel;
	}

	public int getEmbeddingDimensions() {
		return embeddingDimensions;
	}

	public void setEmbeddingDimensions(int embeddingDimensions) {
		this.embeddingDimensions = embeddingDimensions;
	}

	public double getTemperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}

	public int getMaxOutputTokens() {
		return maxOutputTokens;
	}

	public void setMaxOutputTokens(int maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	public boolean isConfigured() {
		return StringUtils.hasText(apiKey);
	}
}
