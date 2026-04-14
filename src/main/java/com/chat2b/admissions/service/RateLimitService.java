package com.chat2b.admissions.service;

import com.chat2b.admissions.config.AppProperties;
import com.chat2b.admissions.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

	private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

	private final AppProperties appProperties;
	private final Clock clock;
	private final ConcurrentHashMap<String, RollingCounter> counters = new ConcurrentHashMap<>();

	@Autowired
	public RateLimitService(AppProperties appProperties) {
		this(appProperties, Clock.systemDefaultZone());
	}

	RateLimitService(AppProperties appProperties, Clock clock) {
		this.appProperties = appProperties;
		this.clock = clock;
	}

	public void check(String ipAddress, String sessionId) {
		if (StringUtils.hasText(ipAddress)) {
			increment("ip:" + ipAddress.trim(), appProperties.getIpMinuteLimit(), appProperties.getIpDailyLimit(), "IP");
		}
		if (StringUtils.hasText(sessionId)) {
			increment("session:" + sessionId.trim(), appProperties.getSessionMinuteLimit(), appProperties.getSessionDailyLimit(), "session");
		}
	}

	private void increment(String key, int minuteLimit, int dailyLimit, String scope) {
		Instant now = clock.instant();
		LocalDate today = LocalDate.now(clock);
		RollingCounter counter = counters.computeIfAbsent(key, ignored -> new RollingCounter(today));
		synchronized (counter) {
			counter.resetIfNeeded(today);
			counter.prune(now.minusSeconds(Math.max(1, appProperties.getRateLimitWindowSeconds())).toEpochMilli());

			if (minuteLimit > 0 && counter.recentRequestTimes.size() >= minuteLimit) {
				log.warn("{} minute rate limit exceeded.", scope);
				throw new RateLimitExceededException("Too many requests. Please wait a moment and try again.");
			}
			if (dailyLimit > 0 && counter.dailyCount >= dailyLimit) {
				log.warn("{} daily rate limit exceeded.", scope);
				throw new RateLimitExceededException("Daily question limit exceeded. Please try again tomorrow.");
			}

			counter.recentRequestTimes.addLast(now.toEpochMilli());
			counter.dailyCount++;
		}
	}

	private static final class RollingCounter {

		private LocalDate date;
		private int dailyCount;
		private final ArrayDeque<Long> recentRequestTimes = new ArrayDeque<>();

		private RollingCounter(LocalDate date) {
			this.date = date;
			this.dailyCount = 0;
		}

		private void resetIfNeeded(LocalDate today) {
			if (!date.equals(today)) {
				date = today;
				dailyCount = 0;
				recentRequestTimes.clear();
			}
		}

		private void prune(long thresholdEpochMilli) {
			while (!recentRequestTimes.isEmpty() && recentRequestTimes.peekFirst() < thresholdEpochMilli) {
				recentRequestTimes.removeFirst();
			}
		}
	}
}
