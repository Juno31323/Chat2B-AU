package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitServiceTest {

	@Test
	void blocksRequestsWhenMinuteLimitIsExceeded() {
		AppProperties properties = properties();
		properties.setIpMinuteLimit(2);
		properties.setIpDailyLimit(10);
		MutableClock clock = new MutableClock(Instant.parse("2026-04-14T00:00:00Z"));
		RateLimitService service = new RateLimitService(properties, clock);

		assertDoesNotThrow(() -> service.check("127.0.0.1", "session-1"));
		assertDoesNotThrow(() -> service.check("127.0.0.1", "session-2"));
		assertThrows(RateLimitExceededException.class, () -> service.check("127.0.0.1", "session-3"));
	}

	@Test
	void allowsRequestsAgainAfterWindowPasses() {
		AppProperties properties = properties();
		properties.setSessionMinuteLimit(1);
		properties.setSessionDailyLimit(10);
		properties.setRateLimitWindowSeconds(60);
		MutableClock clock = new MutableClock(Instant.parse("2026-04-14T00:00:00Z"));
		RateLimitService service = new RateLimitService(properties, clock);

		assertDoesNotThrow(() -> service.check("127.0.0.1", "same-session"));
		assertThrows(RateLimitExceededException.class, () -> service.check("127.0.0.1", "same-session"));

		clock.advanceSeconds(61);

		assertDoesNotThrow(() -> service.check("127.0.0.1", "same-session"));
	}

	@Test
	void resetsDailyLimitOnNextDay() {
		AppProperties properties = properties();
		properties.setIpMinuteLimit(10);
		properties.setIpDailyLimit(1);
		MutableClock clock = new MutableClock(Instant.parse("2026-04-14T00:00:00Z"));
		RateLimitService service = new RateLimitService(properties, clock);

		assertDoesNotThrow(() -> service.check("127.0.0.1", null));
		assertThrows(RateLimitExceededException.class, () -> service.check("127.0.0.1", null));

		clock.advanceSeconds(24 * 60 * 60L);

		assertDoesNotThrow(() -> service.check("127.0.0.1", null));
	}

	private AppProperties properties() {
		AppProperties properties = new AppProperties();
		properties.setRateLimitWindowSeconds(60);
		properties.setIpMinuteLimit(30);
		properties.setIpDailyLimit(200);
		properties.setSessionMinuteLimit(12);
		properties.setSessionDailyLimit(80);
		return properties;
	}

	private static final class MutableClock extends Clock {

		private Instant current;

		private MutableClock(Instant current) {
			this.current = current;
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current;
		}

		private void advanceSeconds(long seconds) {
			current = current.plusSeconds(seconds);
		}
	}
}
