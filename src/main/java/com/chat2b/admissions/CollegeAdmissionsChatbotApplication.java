package com.chat2b.admissions;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.config.GeminiProperties;
import com.chat2b.admissions.config.GenerationProperties;
import com.chat2b.admissions.config.OpenAiProperties;
import com.chat2b.admissions.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, GeminiProperties.class, GenerationProperties.class, OpenAiProperties.class})
public class CollegeAdmissionsChatbotApplication {

	private static final Logger log = LoggerFactory.getLogger(CollegeAdmissionsChatbotApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(CollegeAdmissionsChatbotApplication.class, args);
	}

	@Bean
	ApplicationRunner bootstrapAdmissionsDocuments(
		DocumentIngestionService documentIngestionService,
		AppProperties appProperties
	) {
		return args -> {
			if (!appProperties.isBootstrapOnStartup()) {
				log.info("Admissions document bootstrap is disabled.");
				return;
			}
			try {
				boolean forceReindex = appProperties.isForceReindex() || args.containsOption("force-reindex");
				var decision = documentIngestionService.evaluateReindex(appProperties.getBootstrapLocation(), forceReindex);
				if (!decision.required()) {
					log.info(
						"Admissions document bootstrap skipped. reason={} indexName={} indexVersion={} corpusHash={} retrievalConfigHash={}",
						decision.reason(),
						decision.expected().indexName(),
						decision.expected().indexVersion(),
						decision.expected().corpusHash(),
						decision.expected().retrievalConfigHash()
					);
					return;
				}
				if (appProperties.isBootstrapIfEmptyOnly() && "KNOWLEDGE_BASE_EMPTY".equals(decision.reason())) {
					log.info("Admissions document bootstrap will run because the knowledge base is empty.");
				}
				log.info(
					"Admissions document bootstrap will reindex. reason={} indexName={} indexVersion={} expectedDocuments={} expectedChunks={} corpusHash={} retrievalConfigHash={}",
					decision.reason(),
					decision.expected().indexName(),
					decision.expected().indexVersion(),
					decision.expected().documentCount(),
					decision.expected().chunkCount(),
					decision.expected().corpusHash(),
					decision.expected().retrievalConfigHash()
				);
				var summary = documentIngestionService.reindex(appProperties.getBootstrapLocation());
				log.info(
					"Admissions knowledge base loaded with {} documents and {} chunks using {} embeddings.",
					summary.documentCount(),
					summary.chunkCount(),
					summary.embeddingMode()
				);
			} catch (RuntimeException exception) {
				log.error(
					"Admissions document bootstrap failed during startup. The app will continue using the existing knowledge base if available. Cause: {}",
					exception.getMessage()
				);
			}
		};
	}

}
