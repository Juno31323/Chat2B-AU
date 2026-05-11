package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.model.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefusalGuardServiceTest {

	private final AppProperties appProperties = new AppProperties();
	private final RefusalGuardService refusalGuardService = new RefusalGuardService(appProperties);

	@Test
	void allowsSupportedAdmissionsQuestions() {
		List<QuestionFixture> fixtures = List.of(
			new QuestionFixture("수시 1차 원서접수 기간 알려줘", "수시 1차 원서접수 기간은 2025년 9월 8일부터 9월 30일까지입니다."),
			new QuestionFixture("면접 준비물", "면접 준비물은 신분증과 수험표이며 전형별 안내를 확인해야 합니다."),
			new QuestionFixture("기숙사비 얼마", "기숙사비는 학기별 납부 기준에 따라 안내되며 모집요강의 기숙사비 항목을 확인합니다."),
			new QuestionFixture("신입생 장학금", "신입생 장학금은 새싹키움장학금 등 신입생 지원 장학 제도를 기준으로 합니다."),
			new QuestionFixture("자율전공학과 뭐야", "자율전공학과는 2년제 학과로 모집단위에 포함되어 있습니다.")
		);

		for (QuestionFixture fixture : fixtures) {
			RefusalGuardService.RefusalDecision decision = refusalGuardService.evaluate(
				fixture.question(),
				List.of(chunk("안산대학교 2026 입학 안내", fixture.content(), 0.62d))
			);

			assertThat(decision.allowed())
				.as(fixture.question())
				.isTrue();
		}
	}

	@Test
	void refusesOutOfDocumentQuestions() {
		List<QuestionFixture> fixtures = List.of(
			new QuestionFixture("안산대학교 오늘 날씨 알려줘", "안산대학교 수시 1차 전형일정과 원서접수 기간을 안내합니다."),
			new QuestionFixture("내 학번 알려줘", "안산대학교 입학 FAQ는 원서접수와 합격자 발표를 안내합니다."),
			new QuestionFixture("삼성전자 주가 알려줘", "안산대학교 모집요강은 전형일정과 모집단위를 안내합니다."),
			new QuestionFixture("2030학년도 수강신청 일정 알려줘", "2026학년도 수시 1차 전형일정과 등록 기간을 안내합니다."),
			new QuestionFixture("존재하지 않는 장학금 신청 기간 알려줘", "신입생 장학금 신청과 국가장학금 안내를 확인해야 합니다."),
			new QuestionFixture("안산대학교 총장의 개인 연락처 알려줘", "총장 인사말과 대학 비전을 소개합니다.")
		);

		for (QuestionFixture fixture : fixtures) {
			RefusalGuardService.RefusalDecision decision = refusalGuardService.evaluate(
				fixture.question(),
				List.of(chunk("안산대학교 공지", fixture.content(), 0.71d))
			);

			assertThat(decision.allowed())
				.as(fixture.question())
				.isFalse();
			assertThat(decision.message()).contains("제공된 공지에서 확인되지 않습니다");
		}
	}

	@Test
	void refusesWhenDenseScoreIsTooLow() {
		RefusalGuardService.RefusalDecision decision = refusalGuardService.evaluate(
			"수시 1차 원서접수 기간 알려줘",
			List.of(chunk("안산대학교 2026 입학 안내", "수시 1차 원서접수 기간을 안내합니다.", 0.05d))
		);

		assertThat(decision.allowed()).isFalse();
		assertThat(decision.status()).isEqualTo("LOW_DENSE_SCORE");
	}

	@Test
	void refusesWhenRetrievalIsEmpty() {
		RefusalGuardService.RefusalDecision decision = refusalGuardService.evaluate("수시 1차 일정", List.of());

		assertThat(decision.allowed()).isFalse();
		assertThat(decision.status()).isEqualTo("NO_RETRIEVAL");
	}

	private RetrievedChunk chunk(String title, String content, double denseScore) {
		return new RetrievedChunk(
			1L,
			1L,
			"test_notice_001",
			title,
			"https://iphak.ansan.ac.kr/example",
			Instant.parse("2025-09-01T00:00:00Z"),
			content,
			null,
			"테스트",
			denseScore,
			1
		);
	}

	private record QuestionFixture(String question, String content) {
	}
}
