package com.chat2b.admissions.support;

import com.chat2b.admissions.controller.ChatController;
import com.chat2b.admissions.model.ChatRequest;
import com.chat2b.admissions.model.ChatResponse;
import com.chat2b.admissions.model.SourceReference;
import com.chat2b.admissions.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KoreanEncodingSmokeTest {

	@TempDir
	Path tempDir;

	@Test
	void writesAndReadsKoreanJsonlAsUtf8WithoutAsciiEscaping() throws Exception {
		Path jsonl = tempDir.resolve("answers.jsonl");
		ExperimentJsonlWriter writer = new ExperimentJsonlWriter();

		writer.write(jsonl, List.of(
			Map.of(
				"notice_title", "안산대학교 한글 공지 제목",
				"question", "수시 1차 원서접수 기간 알려줘",
				"answer", "제공된 공지 기준으로 답변합니다.",
				"source_title", "안산대학교 2026 전형일정 등록"
			)
		));

		String raw = Files.readString(jsonl, StandardCharsets.UTF_8);
		assertThat(raw).contains("안산대학교 한글 공지 제목");
		assertThat(raw).contains("수시 1차 원서접수 기간 알려줘");
		assertThat(raw).doesNotContain("\\uC548");

		List<JsonNode> records = writer.read(jsonl);
		assertThat(records).hasSize(1);
		assertThat(records.get(0).path("notice_title").asText()).isEqualTo("안산대학교 한글 공지 제목");
		assertThat(records.get(0).path("answer").asText()).isEqualTo("제공된 공지 기준으로 답변합니다.");
	}

	@Test
	void apiResponseKeepsKoreanAndUtf8Charset() throws Exception {
		ChatService chatService = mock(ChatService.class);
		ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
		ObjectMapper objectMapper = new ObjectMapper();
		MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new ChatController(chatService, clientIpResolver))
			.addFilter(new org.springframework.web.filter.CharacterEncodingFilter(StandardCharsets.UTF_8.name(), true, true))
			.build();

		when(clientIpResolver.resolve(any())).thenReturn("127.0.0.1");
		when(chatService.answer(any(ChatRequest.class), anyString())).thenReturn(new ChatResponse(
			"한글 답변 출력",
			"demo",
			"MATCHED",
			List.of(new SourceReference(
				"안산대학교 2026 전형일정 등록",
				"한글 source title 출력",
				0.91d,
				"안산대학교 2026 전형일정 등록",
				"https://iphak.ansan.ac.kr",
				Instant.parse("2025-09-01T00:00:00Z")
			)),
			null,
			Instant.parse("2026-05-08T00:00:00Z")
		));

		String response = mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
				.accept(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"question", "한글 질문 입력",
					"sessionId", "utf8-session"
				))))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("charset=UTF-8")))
			.andExpect(content().encoding(StandardCharsets.UTF_8))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(response).contains("한글 답변 출력");
		assertThat(response).contains("안산대학교 2026 전형일정 등록");
		assertThat(response).doesNotContain("\\uD55C");
	}
}
