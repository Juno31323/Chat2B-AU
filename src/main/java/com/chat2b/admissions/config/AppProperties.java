package com.chat2b.admissions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private boolean bootstrapOnStartup = true;
	private boolean bootstrapIfEmptyOnly = true;
	private boolean forceReindex = false;
	private String bootstrapLocation = "classpath:admissions-docs/*";
	private String indexName = "text_only";
	private String corpusProfile = "text_only";
	private String indexVersion = "text_only_v1";
	private String tokenizer = "unicode-letter-number-v1";
	private int chunkSize = 700;
	private int chunkOverlap = 0;
	private int embeddingDimensions = 256;
	private boolean pgvectorEnabled = true;
	private boolean pgvectorIndexEnabled = true;
	private int retrievalTopK = 4;
	private int hybridTopK = 5;
	private int bm25TopK = 50;
	private int denseTopK = 50;
	private int rrfK = 60;
	private String fusionMethod = "rrf";
	private double minSimilarity = 0.18d;
	private double refusalMinDenseScore = 0.22d;
	private int refusalMinTokenOverlap = 1;
	private double refusalMinTokenCoverage = 0.30d;
	private int refusalEvidenceTopK = 40;
	private List<String> refusalOutOfDomainCues = new ArrayList<>(List.of(
		"날씨", "학번", "삼성전자", "주가", "2030학년도", "존재하지", "개인 연락처"
	));
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

	public boolean isForceReindex() {
		return forceReindex;
	}

	public void setForceReindex(boolean forceReindex) {
		this.forceReindex = forceReindex;
	}

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getCorpusProfile() {
		return corpusProfile;
	}

	public void setCorpusProfile(String corpusProfile) {
		this.corpusProfile = corpusProfile;
	}

	public String getIndexVersion() {
		return indexVersion;
	}

	public void setIndexVersion(String indexVersion) {
		this.indexVersion = indexVersion;
	}

	public String getTokenizer() {
		return tokenizer;
	}

	public void setTokenizer(String tokenizer) {
		this.tokenizer = tokenizer;
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	public int getChunkOverlap() {
		return chunkOverlap;
	}

	public void setChunkOverlap(int chunkOverlap) {
		this.chunkOverlap = chunkOverlap;
	}

	public int getEmbeddingDimensions() {
		return embeddingDimensions;
	}

	public void setEmbeddingDimensions(int embeddingDimensions) {
		this.embeddingDimensions = embeddingDimensions;
	}

	public boolean isPgvectorEnabled() {
		return pgvectorEnabled;
	}

	public void setPgvectorEnabled(boolean pgvectorEnabled) {
		this.pgvectorEnabled = pgvectorEnabled;
	}

	public boolean isPgvectorIndexEnabled() {
		return pgvectorIndexEnabled;
	}

	public void setPgvectorIndexEnabled(boolean pgvectorIndexEnabled) {
		this.pgvectorIndexEnabled = pgvectorIndexEnabled;
	}

	public int getRetrievalTopK() {
		return retrievalTopK;
	}

	public void setRetrievalTopK(int retrievalTopK) {
		this.retrievalTopK = retrievalTopK;
	}

	public int getHybridTopK() {
		return hybridTopK;
	}

	public void setHybridTopK(int hybridTopK) {
		this.hybridTopK = hybridTopK;
	}

	public int getBm25TopK() {
		return bm25TopK;
	}

	public void setBm25TopK(int bm25TopK) {
		this.bm25TopK = bm25TopK;
	}

	public int getDenseTopK() {
		return denseTopK;
	}

	public void setDenseTopK(int denseTopK) {
		this.denseTopK = denseTopK;
	}

	public int getRrfK() {
		return rrfK;
	}

	public void setRrfK(int rrfK) {
		this.rrfK = rrfK;
	}

	public String getFusionMethod() {
		return fusionMethod;
	}

	public void setFusionMethod(String fusionMethod) {
		this.fusionMethod = fusionMethod;
	}

	public double getMinSimilarity() {
		return minSimilarity;
	}

	public void setMinSimilarity(double minSimilarity) {
		this.minSimilarity = minSimilarity;
	}

	public double getRefusalMinDenseScore() {
		return refusalMinDenseScore;
	}

	public void setRefusalMinDenseScore(double refusalMinDenseScore) {
		this.refusalMinDenseScore = refusalMinDenseScore;
	}

	public int getRefusalMinTokenOverlap() {
		return refusalMinTokenOverlap;
	}

	public void setRefusalMinTokenOverlap(int refusalMinTokenOverlap) {
		this.refusalMinTokenOverlap = refusalMinTokenOverlap;
	}

	public double getRefusalMinTokenCoverage() {
		return refusalMinTokenCoverage;
	}

	public void setRefusalMinTokenCoverage(double refusalMinTokenCoverage) {
		this.refusalMinTokenCoverage = refusalMinTokenCoverage;
	}

	public int getRefusalEvidenceTopK() {
		return refusalEvidenceTopK;
	}

	public void setRefusalEvidenceTopK(int refusalEvidenceTopK) {
		this.refusalEvidenceTopK = refusalEvidenceTopK;
	}

	public List<String> getRefusalOutOfDomainCues() {
		return refusalOutOfDomainCues;
	}

	public void setRefusalOutOfDomainCues(List<String> refusalOutOfDomainCues) {
		this.refusalOutOfDomainCues = refusalOutOfDomainCues == null ? new ArrayList<>() : new ArrayList<>(refusalOutOfDomainCues);
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
