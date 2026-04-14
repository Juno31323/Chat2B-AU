package com.chat2b.admissions.support;

import com.chat2b.admissions.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

	private final AppProperties appProperties;

	public ClientIpResolver(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	public String resolve(HttpServletRequest request) {
		String remoteAddress = normalize(request.getRemoteAddr());
		if (!appProperties.isTrustForwardHeaders() || !isTrustedProxy(remoteAddress)) {
			return remoteAddress;
		}

		String forwardedFor = firstForwardedAddress(request.getHeader("X-Forwarded-For"));
		if (StringUtils.hasText(forwardedFor)) {
			return normalize(forwardedFor);
		}

		String realIp = normalize(request.getHeader("X-Real-IP"));
		return StringUtils.hasText(realIp) ? realIp : remoteAddress;
	}

	private boolean isTrustedProxy(String remoteAddress) {
		String normalizedRemoteAddress = normalize(remoteAddress);
		if (!StringUtils.hasText(normalizedRemoteAddress)) {
			return false;
		}
		return appProperties.getTrustedProxyAddresses().stream()
			.anyMatch("*"::equals)
			|| appProperties.getTrustedProxyAddresses().stream()
			.map(this::normalize)
			.anyMatch(normalizedRemoteAddress::equals);
	}

	private String firstForwardedAddress(String forwardedFor) {
		if (!StringUtils.hasText(forwardedFor)) {
			return null;
		}
		String[] parts = forwardedFor.split(",");
		return parts.length == 0 ? null : parts[0].trim();
	}

	private String normalize(String address) {
		if (!StringUtils.hasText(address)) {
			return null;
		}
		String normalized = address.trim();
		if (normalized.startsWith("::ffff:")) {
			normalized = normalized.substring("::ffff:".length());
		}
		if ("0:0:0:0:0:0:0:1".equals(normalized)) {
			return "::1";
		}
		return normalized;
	}
}
