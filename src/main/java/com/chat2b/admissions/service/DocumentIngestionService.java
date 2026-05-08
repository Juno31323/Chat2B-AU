package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.model.IndexMetadata;
import com.chat2b.admissions.repository.AdmissionsRepository;
import com.chat2b.admissions.support.CorpusHashUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DocumentIngestionService {

	private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
	private static final String GEMINI_EMBEDDING_MODE = "gemini";
	private static final String HASHED_EMBEDDING_MODE = "hashed";

	private final AdmissionsRepository repository;
	private final AppProperties appProperties;
	private final TextChunker textChunker;
	private final GeminiGateway geminiGateway;
	private final HashedEmbeddingService hashedEmbeddingService;
	private final Bm25SearchService bm25SearchService;
	private final ResourcePatternResolver resourcePatternResolver;
	private volatile String currentEmbeddingMode;

	public DocumentIngestionService(
		AdmissionsRepository repository,
		AppProperties appProperties,
		TextChunker textChunker,
		GeminiGateway geminiGateway,
		HashedEmbeddingService hashedEmbeddingService,
		Bm25SearchService bm25SearchService,
		ResourcePatternResolver resourcePatternResolver
	) {
		this.repository = repository;
		this.appProperties = appProperties;
		this.textChunker = textChunker;
		this.geminiGateway = geminiGateway;
		this.hashedEmbeddingService = hashedEmbeddingService;
		this.bm25SearchService = bm25SearchService;
		this.resourcePatternResolver = resourcePatternResolver;
		this.currentEmbeddingMode = geminiGateway.isConfigured() ? GEMINI_EMBEDDING_MODE : HASHED_EMBEDDING_MODE;
	}

	public synchronized IngestionSummary reindex(String locationPattern) {
		List<ParsedDocument> parsedDocuments = loadParsedDocuments(locationPattern);

		PreparedIndex preparedIndex = prepareIndex(parsedDocuments);
		repository.clearKnowledgeBase();

		int documentCount = 0;
		int chunkCount = 0;
		for (PreparedDocument document : preparedIndex.documents()) {
			long documentId = repository.insertDocument(document.title(), document.sourcePath(), document.contentType());
			for (PreparedChunk chunk : document.chunks()) {
				repository.insertChunk(
					documentId,
					chunk.chunkIndex(),
					chunk.content(),
					chunk.pageNumber(),
					chunk.sectionName(),
					chunk.embedding(),
					embeddingModelName(preparedIndex.embeddingMode()),
					appProperties.getEmbeddingDimensions(),
					appProperties.getIndexVersion()
				);
			}
			documentCount++;
			chunkCount += document.chunks().size();
			log.info("Indexed document {} with {} chunks.", document.title(), document.chunks().size());
		}
		currentEmbeddingMode = preparedIndex.embeddingMode();
		repository.insertIndexMetadata(
			appProperties.getIndexName(),
			appProperties.getCorpusProfile(),
			appProperties.getIndexVersion(),
			documentCount,
			chunkCount,
			embeddingModelName(preparedIndex.embeddingMode()),
			appProperties.getEmbeddingDimensions(),
			appProperties.getChunkSize(),
			appProperties.getChunkOverlap(),
			appProperties.getTokenizer(),
			corpusHash(parsedDocuments),
			retrievalConfigHash(),
			locationPattern
		);
		bm25SearchService.rebuild();

		return new IngestionSummary(documentCount, chunkCount, currentEmbeddingMode);
	}

	public ReindexDecision evaluateReindex(String locationPattern, boolean forceReindex) {
		List<ParsedDocument> parsedDocuments = loadParsedDocuments(locationPattern);
		String embeddingMode = geminiGateway.isConfigured() ? GEMINI_EMBEDDING_MODE : HASHED_EMBEDDING_MODE;
		ExpectedIndexMetadata expected = expectedMetadata(parsedDocuments, embeddingMode, locationPattern);
		Optional<IndexMetadata> latest = repository.getLatestIndexMetadata(appProperties.getIndexName(), appProperties.getIndexVersion());

		if (forceReindex) {
			return ReindexDecision.required("FORCE_REINDEX", expected, latest.orElse(null));
		}
		if (latest.isEmpty()) {
			return ReindexDecision.required("METADATA_MISSING", expected, null);
		}
		if (!repository.hasKnowledgeBase()) {
			return ReindexDecision.required("KNOWLEDGE_BASE_EMPTY", expected, latest.get());
		}

		String mismatch = mismatchReason(expected, latest.get());
		if (mismatch != null) {
			return ReindexDecision.required(mismatch, expected, latest.get());
		}
		return ReindexDecision.skip("INDEX_UP_TO_DATE", expected, latest.get());
	}

	public String embeddingMode() {
		return currentEmbeddingMode;
	}

	public boolean usesGeminiEmbeddings() {
		return GEMINI_EMBEDDING_MODE.equals(currentEmbeddingMode);
	}

	public String currentEmbeddingModelName() {
		return embeddingModelName(currentEmbeddingMode);
	}

	private PreparedIndex prepareIndex(List<ParsedDocument> parsedDocuments) {
		if (geminiGateway.isConfigured()) {
			try {
				return prepareIndex(parsedDocuments, GEMINI_EMBEDDING_MODE);
			} catch (RuntimeException exception) {
				log.warn(
					"Gemini embeddings were unavailable during reindex. Falling back to hashed embeddings for this run. Cause: {}",
					exception.getMessage()
				);
			}
		}
		return prepareIndex(parsedDocuments, HASHED_EMBEDDING_MODE);
	}

	private PreparedIndex prepareIndex(List<ParsedDocument> parsedDocuments, String embeddingMode) {
		List<PreparedDocument> preparedDocuments = new ArrayList<>();
		for (ParsedDocument document : parsedDocuments) {
			List<TextChunker.ChunkCandidate> candidates = textChunker.chunkSections(document.sections());
			List<PreparedChunk> preparedChunks = new ArrayList<>();
			int chunkIndex = 0;
			for (TextChunker.ChunkCandidate chunk : candidates) {
				preparedChunks.add(
					new PreparedChunk(
						chunkIndex++,
						chunk.content(),
						chunk.pageNumber(),
						chunk.sectionName(),
						embed(document.title(), chunk, embeddingMode)
					)
				);
			}
			preparedDocuments.add(
				new PreparedDocument(
					document.title(),
					document.sourcePath(),
					document.contentType(),
					preparedChunks
				)
			);
		}
		return new PreparedIndex(preparedDocuments, embeddingMode);
	}

	private float[] embed(String documentTitle, TextChunker.ChunkCandidate chunk, String embeddingMode) {
		return GEMINI_EMBEDDING_MODE.equals(embeddingMode)
			? geminiGateway.createDocumentEmbedding(chunk.content(), summarizeTitle(documentTitle, chunk.sectionName()))
			: hashedEmbeddingService.embed(chunk.content());
	}

	public String embeddingModelName(String embeddingMode) {
		return GEMINI_EMBEDDING_MODE.equals(embeddingMode)
			? geminiGateway.embeddingModelName()
			: "hashed-" + appProperties.getEmbeddingDimensions();
	}

	public String retrievalConfigHash() {
		return CorpusHashUtils.sha256(List.of(
			"retrievalTopK=" + appProperties.getRetrievalTopK(),
			"hybridTopK=" + appProperties.getHybridTopK(),
			"bm25TopK=" + appProperties.getBm25TopK(),
			"denseTopK=" + appProperties.getDenseTopK(),
			"rrfK=" + appProperties.getRrfK(),
			"fusionMethod=" + appProperties.getFusionMethod(),
			"minSimilarity=" + appProperties.getMinSimilarity(),
			"responseSourceLimit=" + appProperties.getResponseSourceLimit(),
			"refusalMinDenseScore=" + appProperties.getRefusalMinDenseScore(),
			"refusalMinTokenOverlap=" + appProperties.getRefusalMinTokenOverlap(),
			"refusalMinTokenCoverage=" + appProperties.getRefusalMinTokenCoverage(),
			"refusalEvidenceTopK=" + appProperties.getRefusalEvidenceTopK(),
			"refusalOutOfDomainCues=" + String.join(",", appProperties.getRefusalOutOfDomainCues())
		));
	}

	private ExpectedIndexMetadata expectedMetadata(List<ParsedDocument> parsedDocuments, String embeddingMode, String locationPattern) {
		return new ExpectedIndexMetadata(
			appProperties.getIndexName(),
			appProperties.getCorpusProfile(),
			appProperties.getIndexVersion(),
			parsedDocuments.size(),
			plannedChunkCount(parsedDocuments),
			corpusHash(parsedDocuments),
			embeddingModelName(embeddingMode),
			appProperties.getEmbeddingDimensions(),
			appProperties.getChunkSize(),
			appProperties.getChunkOverlap(),
			appProperties.getTokenizer(),
			retrievalConfigHash(),
			locationPattern
		);
	}

	private int plannedChunkCount(List<ParsedDocument> parsedDocuments) {
		int chunkCount = 0;
		for (ParsedDocument document : parsedDocuments) {
			chunkCount += textChunker.chunkSections(document.sections()).size();
		}
		return chunkCount;
	}

	private String mismatchReason(ExpectedIndexMetadata expected, IndexMetadata actual) {
		if (!Objects.equals(expected.indexName(), actual.indexName())) {
			return "INDEX_NAME_CHANGED";
		}
		if (!Objects.equals(expected.indexVersion(), actual.indexVersion())) {
			return "INDEX_VERSION_CHANGED";
		}
		if (expected.documentCount() != actual.documentCount()) {
			return "DOCUMENT_COUNT_CHANGED";
		}
		if (expected.chunkCount() != actual.chunkCount()) {
			return "CHUNK_COUNT_CHANGED";
		}
		if (!Objects.equals(expected.corpusHash(), actual.corpusHash())) {
			return "CORPUS_HASH_CHANGED";
		}
		if (!Objects.equals(expected.embeddingModel(), actual.embeddingModel())) {
			return "EMBEDDING_MODEL_CHANGED";
		}
		if (expected.embeddingDim() != actual.embeddingDim()) {
			return "EMBEDDING_DIM_CHANGED";
		}
		if (expected.chunkSize() != actual.chunkSize()) {
			return "CHUNK_SIZE_CHANGED";
		}
		if (expected.chunkOverlap() != actual.chunkOverlap()) {
			return "CHUNK_OVERLAP_CHANGED";
		}
		if (!Objects.equals(expected.tokenizer(), actual.tokenizer())) {
			return "TOKENIZER_CHANGED";
		}
		if (!Objects.equals(expected.retrievalConfigHash(), actual.retrievalConfigHash())) {
			return "RETRIEVAL_CONFIG_CHANGED";
		}
		if (!Objects.equals(expected.sourceDataPath(), actual.sourceDataPath())) {
			return "SOURCE_DATA_PATH_CHANGED";
		}
		return null;
	}

	private String corpusHash(List<ParsedDocument> parsedDocuments) {
		List<String> parts = new ArrayList<>();
		for (ParsedDocument document : parsedDocuments) {
			parts.add(document.title());
			parts.add(document.sourcePath());
			parts.add(document.contentType());
			for (TextChunker.SectionText section : document.sections()) {
				parts.add(section.sectionName());
				parts.add(String.valueOf(section.pageNumber()));
				parts.add(section.text());
			}
		}
		return CorpusHashUtils.sha256(parts);
	}

	private String summarizeTitle(String documentTitle, String sectionName) {
		String normalized = String.join(
			" / ",
			documentTitle == null ? "" : documentTitle.trim(),
			sectionName == null ? "" : sectionName.trim()
		).replaceAll("\\s+", " ").trim().replaceFirst(" / $", "");
		if (normalized.length() <= 80) {
			return normalized;
		}
		return normalized.substring(0, 80);
	}

	private List<Resource> resolveResources(String locationPattern) {
		try {
			Resource[] resources = resourcePatternResolver.getResources(locationPattern);
			List<Resource> supported = new ArrayList<>();
			for (Resource resource : resources) {
				String filename = resource.getFilename();
				if (filename == null) {
					continue;
				}
				String extension = extensionOf(filename);
				if (List.of("md", "txt", "pdf").contains(extension)) {
					supported.add(resource);
				}
			}
			return supported;
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to load admissions documents.", exception);
		}
	}

	private List<ParsedDocument> loadParsedDocuments(String locationPattern) {
		List<Resource> resources = resolveResources(locationPattern);
		if (resources.isEmpty()) {
			throw new IllegalStateException("No admissions documents were found for " + locationPattern);
		}

		List<ParsedDocument> parsedDocuments = new ArrayList<>();
		for (Resource resource : resources) {
			ParsedDocument document = parse(resource);
			if (document.sections().isEmpty()) {
				continue;
			}
			parsedDocuments.add(document);
		}
		if (parsedDocuments.isEmpty()) {
			throw new IllegalStateException("Admissions documents were loaded, but no readable sections were found.");
		}
		return parsedDocuments;
	}

	private ParsedDocument parse(Resource resource) {
		String filename = resource.getFilename();
		if (filename == null) {
			throw new IllegalStateException("Admissions document filename is missing.");
		}
		String extension = extensionOf(filename);
		return switch (extension) {
			case "md", "txt" -> parseTextDocument(resource, filename, extension);
			case "pdf" -> parsePdfDocument(resource, filename);
			default -> throw new IllegalStateException("Unsupported admissions document type: " + filename);
		};
	}

	private ParsedDocument parseTextDocument(Resource resource, String filename, String extension) {
		try {
			String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			List<TextChunker.SectionText> sections = new ArrayList<>();
			String currentSection = titleFromFilename(filename);
			StringBuilder builder = new StringBuilder();
			for (String line : raw.replace("\r", "").split("\n")) {
				if (line.matches("^#{1,6}\\s+.+")) {
					appendSection(sections, currentSection, builder);
					currentSection = line.replaceFirst("^#{1,6}\\s+", "").trim();
					continue;
				}
				builder.append(line).append("\n");
			}
			appendSection(sections, currentSection, builder);
			return new ParsedDocument(titleFromFilename(filename), filename, extension, sections);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to read admissions document " + filename, exception);
		}
	}

	private ParsedDocument parsePdfDocument(Resource resource, String filename) {
		try (PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
			PDFTextStripper stripper = new PDFTextStripper();
			List<TextChunker.SectionText> sections = new ArrayList<>();
			for (int page = 1; page <= document.getNumberOfPages(); page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				String text = stripper.getText(document).trim();
				if (StringUtils.hasText(text)) {
					sections.add(new TextChunker.SectionText("Page " + page, page, text));
				}
			}
			return new ParsedDocument(titleFromFilename(filename), filename, "pdf", sections);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to parse PDF admissions document " + filename, exception);
		}
	}

	private void appendSection(List<TextChunker.SectionText> sections, String sectionName, StringBuilder builder) {
		String content = builder.toString().trim();
		if (StringUtils.hasText(content)) {
			sections.add(new TextChunker.SectionText(sectionName, null, content));
		}
		builder.setLength(0);
	}

	private String titleFromFilename(String filename) {
		return filename.replaceFirst("\\.[^.]+$", "").replace('_', ' ').trim();
	}

	private String extensionOf(String filename) {
		int dotIndex = filename.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex == filename.length() - 1) {
			return "";
		}
		return filename.substring(dotIndex + 1).toLowerCase();
	}

	public record IngestionSummary(int documentCount, int chunkCount, String embeddingMode) {
	}

	public record ExpectedIndexMetadata(
		String indexName,
		String corpusProfile,
		String indexVersion,
		int documentCount,
		int chunkCount,
		String corpusHash,
		String embeddingModel,
		int embeddingDim,
		int chunkSize,
		int chunkOverlap,
		String tokenizer,
		String retrievalConfigHash,
		String sourceDataPath
	) {
	}

	public record ReindexDecision(
		boolean required,
		String reason,
		ExpectedIndexMetadata expected,
		IndexMetadata actual
	) {
		static ReindexDecision required(String reason, ExpectedIndexMetadata expected, IndexMetadata actual) {
			return new ReindexDecision(true, reason, expected, actual);
		}

		static ReindexDecision skip(String reason, ExpectedIndexMetadata expected, IndexMetadata actual) {
			return new ReindexDecision(false, reason, expected, actual);
		}
	}

	private record PreparedIndex(
		List<PreparedDocument> documents,
		String embeddingMode
	) {
	}

	private record PreparedDocument(
		String title,
		String sourcePath,
		String contentType,
		List<PreparedChunk> chunks
	) {
	}

	private record PreparedChunk(
		int chunkIndex,
		String content,
		Integer pageNumber,
		String sectionName,
		float[] embedding
	) {
	}

	private record ParsedDocument(
		String title,
		String sourcePath,
		String contentType,
		List<TextChunker.SectionText> sections
	) {
	}
}
