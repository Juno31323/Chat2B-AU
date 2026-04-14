package com.chat2b.admissions.service;

import com.chat2b.admissions.repository.AdmissionsRepository;
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

@Service
public class DocumentIngestionService {

	private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
	private static final String GEMINI_EMBEDDING_MODE = "gemini";
	private static final String HASHED_EMBEDDING_MODE = "hashed";

	private final AdmissionsRepository repository;
	private final TextChunker textChunker;
	private final GeminiGateway geminiGateway;
	private final HashedEmbeddingService hashedEmbeddingService;
	private final ResourcePatternResolver resourcePatternResolver;
	private volatile String currentEmbeddingMode;

	public DocumentIngestionService(
		AdmissionsRepository repository,
		TextChunker textChunker,
		GeminiGateway geminiGateway,
		HashedEmbeddingService hashedEmbeddingService,
		ResourcePatternResolver resourcePatternResolver
	) {
		this.repository = repository;
		this.textChunker = textChunker;
		this.geminiGateway = geminiGateway;
		this.hashedEmbeddingService = hashedEmbeddingService;
		this.resourcePatternResolver = resourcePatternResolver;
		this.currentEmbeddingMode = geminiGateway.isConfigured() ? GEMINI_EMBEDDING_MODE : HASHED_EMBEDDING_MODE;
	}

	public synchronized IngestionSummary reindex(String locationPattern) {
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
					chunk.embedding()
				);
			}
			documentCount++;
			chunkCount += document.chunks().size();
			log.info("Indexed document {} with {} chunks.", document.title(), document.chunks().size());
		}
		currentEmbeddingMode = preparedIndex.embeddingMode();

		return new IngestionSummary(documentCount, chunkCount, currentEmbeddingMode);
	}

	public String embeddingMode() {
		return currentEmbeddingMode;
	}

	public boolean usesGeminiEmbeddings() {
		return GEMINI_EMBEDDING_MODE.equals(currentEmbeddingMode);
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
