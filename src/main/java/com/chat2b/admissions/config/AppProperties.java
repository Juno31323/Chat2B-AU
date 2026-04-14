package com.chat2b.admissions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private boolean bootstrapOnStartup = true;
	private boolean bootstrapIfEmptyOnly = true;
	private String bootstrapLocation = "classpath:admissions-docs/*";
	private int embeddingDimensions = 256;
	private int retrievalTopK = 4;
	private double minSimilarity = 0.18d;
	private int maxContextChars = 3600;
	private int responseSourceLimit = 3;
	private int ipMinuteLimit = 30;
	private int ipDailyLimit = 200;
	private int sessionMinuteLimit = 12;
	private int sessionDailyLimit = 80;
	private int rateLimitWindowSeconds = 60;
	private boolean trustForwardHeaders = false;
	private List<String> trustedProxyAddresses = new ArrayList<>(List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1"));
	private int adminReindexCooldownSeconds = 300;
	private String adminKey = "";

	public boolean isBootstrapOnStartup() {
		return bootstrapOnStartup;
	}

	public void setBootstrapOnStartup(boolean bootstrapOnStartup) {
		this.bootstrapOnStartup = bootstrapOnStartup;
	}

	public String getBootstrapLocation() {
		return bootstrapLocation;
	}

	public void setBootstrapLocation(String bootstrapLocation) {
		this.bootstrapLocation = bootstrapLocation;
	}

	public boolean isBootstrapIfEmptyOnly() {
		return bootstrapIfEmptyOnly;
	}

	public void setBootstrapIfEmptyOnly(boolean bootstrapIfEmptyOnly) {
		this.bootstrapIfEmptyOnly = bootstrapIfEmptyOnly;
	}

	public int getEmbeddingDimensions() {
		return embeddingDimensions;
	}

	public void setEmbeddingDimensions(int embeddingDimensions) {
		this.embeddingDimensions = embeddingDimensions;
	}

	public int getRetrievalTopK() {
		return retrievalTopK;
	}

	public void setRetrievalTopK(int retrievalTopK) {
		this.retrievalTopK = retrievalTopK;
	}

	public double getMinSimilarity() {
		return minSimilarity;
	}

	public void setMinSimilarity(double minSimilarity) {
		this.minSimilarity = minSimilarity;
	}

	public int getMaxContextChars() {
		return maxContextChars;
	}

	public void setMaxContextChars(int maxContextChars) {
		this.maxContextChars = maxContextChars;
	}

	public int getResponseSourceLimit() {
		return responseSourceLimit;
	}

	public void setResponseSourceLimit(int responseSourceLimit) {
		this.responseSourceLimit = responseSourceLimit;
	}

	public int getIpMinuteLimit() {
		return ipMinuteLimit;
	}

	public void setIpMinuteLimit(int ipMinuteLimit) {
		this.ipMinuteLimit = ipMinuteLimit;
	}

	public int getIpDailyLimit() {
		return ipDailyLimit;
	}

	public void setIpDailyLimit(int ipDailyLimit) {
		this.ipDailyLimit = ipDailyLimit;
	}

	public int getSessionMinuteLimit() {
		return sessionMinuteLimit;
	}

	public void setSessionMinuteLimit(int sessionMinuteLimit) {
		this.sessionMinuteLimit = sessionMinuteLimit;
	}

	public int getSessionDailyLimit() {
		return sessionDailyLimit;
	}

	public void setSessionDailyLimit(int sessionDailyLimit) {
		this.sessionDailyLimit = sessionDailyLimit;
	}

	public int getRateLimitWindowSeconds() {
		return rateLimitWindowSeconds;
	}

	public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) {
		this.rateLimitWindowSeconds = rateLimitWindowSeconds;
	}

	public boolean isTrustForwardHeaders() {
		return trustForwardHeaders;
	}

	public void setTrustForwardHeaders(boolean trustForwardHeaders) {
		this.trustForwardHeaders = trustForwardHeaders;
	}

	public List<String> getTrustedProxyAddresses() {
		return trustedProxyAddresses;
	}

	public void setTrustedProxyAddresses(List<String> trustedProxyAddresses) {
		this.trustedProxyAddresses = trustedProxyAddresses == null ? new ArrayList<>() : new ArrayList<>(trustedProxyAddresses);
	}

	public int getAdminReindexCooldownSeconds() {
		return adminReindexCooldownSeconds;
	}

	public void setAdminReindexCooldownSeconds(int adminReindexCooldownSeconds) {
		this.adminReindexCooldownSeconds = adminReindexCooldownSeconds;
	}

	public String getAdminKey() {
		return adminKey;
	}

	public void setAdminKey(String adminKey) {
		this.adminKey = adminKey;
	}

	public boolean hasConfiguredAdminKey() {
		return StringUtils.hasText(adminKey) && !"change-me".equals(adminKey.trim());
	}
}
