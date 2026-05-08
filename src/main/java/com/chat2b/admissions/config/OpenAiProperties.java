package com.chat2b.admissions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

	private String apiKey;
	private String baseUrl = "https://api.openai.com/v1";

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

	public boolean isConfigured() {
		return StringUtils.hasText(apiKey);
	}
}
