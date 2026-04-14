package com.chat2b.admissions;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.config.GeminiProperties;
import com.chat2b.admissions.repository.AdmissionsRepository;
import com.chat2b.admissions.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, GeminiProperties.class})
public class CollegeAdmissionsChatbotApplication {

	private static final Logger log = LoggerFactory.getLogger(CollegeAdmissionsChatbotApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(CollegeAdmissionsChatbotApplication.class, args);
	}

	@Bean
	ApplicationRunner bootstrapAdmissionsDocuments(
		DocumentIngestionService documentIngestionService,
		AdmissionsRepository admissionsRepository,
		AppProperties appProperties
	) {
		return args -> {
			if (!appProperties.isBootstrapOnStartup()) {
				log.info("Admissions document bootstrap is disabled.");
				return;
			}
			if (appProperties.isBootstrapIfEmptyOnly() && admissionsRepository.hasKnowledgeBase()) {
				log.info("Admissions document bootstrap skipped because an indexed knowledge base already exists.");
				return;
			}
			try {
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
