package com.chat2b.admissions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "generation")
public class GenerationProperties {

	private String provider = "gemini";
	private String model = "gemini-2.5-flash-lite";
	private String importantModel = "";
	private double temperature = 0.0d;
	private int maxOutputTokens = 512;
	private String promptVersion = "grounded_qa_v1";
	private boolean allowMockFallback = true;
	private double inputCostPerMillionTokensUsd = 0.0d;
	private double outputCostPerMillionTokensUsd = 0.0d;
	private double importantInputCostPerMillionTokensUsd = 0.0d;
	private double importantOutputCostPerMillionTokensUsd = 0.0d;

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getImportantModel() {
		return importantModel;
	}

	public void setImportantModel(String importantModel) {
		this.importantModel = importantModel;
	}

	public String selectModel(boolean important) {
		if (important && importantModel != null && !importantModel.isBlank()) {
			return importantModel;
		}
		return model;
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

	public String getPromptVersion() {
		return promptVersion;
	}

	public void setPromptVersion(String promptVersion) {
		this.promptVersion = promptVersion;
	}

	public boolean isAllowMockFallback() {
		return allowMockFallback;
	}

	public void setAllowMockFallback(boolean allowMockFallback) {
		this.allowMockFallback = allowMockFallback;
	}

	public double getInputCostPerMillionTokensUsd() {
		return inputCostPerMillionTokensUsd;
	}

	public void setInputCostPerMillionTokensUsd(double inputCostPerMillionTokensUsd) {
		this.inputCostPerMillionTokensUsd = inputCostPerMillionTokensUsd;
	}

	public double getOutputCostPerMillionTokensUsd() {
		return outputCostPerMillionTokensUsd;
	}

	public void setOutputCostPerMillionTokensUsd(double outputCostPerMillionTokensUsd) {
		this.outputCostPerMillionTokensUsd = outputCostPerMillionTokensUsd;
	}

	public double getImportantInputCostPerMillionTokensUsd() {
		return importantInputCostPerMillionTokensUsd;
	}

	public void setImportantInputCostPerMillionTokensUsd(double importantInputCostPerMillionTokensUsd) {
		this.importantInputCostPerMillionTokensUsd = importantInputCostPerMillionTokensUsd;
	}

	public double getImportantOutputCostPerMillionTokensUsd() {
		return importantOutputCostPerMillionTokensUsd;
	}

	public void setImportantOutputCostPerMillionTokensUsd(double importantOutputCostPerMillionTokensUsd) {
		this.importantOutputCostPerMillionTokensUsd = importantOutputCostPerMillionTokensUsd;
	}
}
