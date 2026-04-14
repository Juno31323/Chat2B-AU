package com.chat2b.admissions.support;

import com.chat2b.admissions.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

	@Test
	void ignoresForwardedHeaderUnlessTrustIsEnabled() {
		AppProperties properties = new AppProperties();
		properties.setTrustForwardHeaders(false);
		ClientIpResolver resolver = new ClientIpResolver(properties);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.10");
		request.addHeader("X-Forwarded-For", "203.0.113.55");

		assertEquals("10.0.0.10", resolver.resolve(request));
	}

	@Test
	void usesForwardedHeaderWhenRemoteProxyIsTrusted() {
		AppProperties properties = new AppProperties();
		properties.setTrustForwardHeaders(true);
		properties.setTrustedProxyAddresses(List.of("127.0.0.1"));
		ClientIpResolver resolver = new ClientIpResolver(properties);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("X-Forwarded-For", "203.0.113.55, 127.0.0.1");

		assertEquals("203.0.113.55", resolver.resolve(request));
	}

	@Test
	void ignoresForwardedHeaderWhenProxyIsNotTrusted() {
		AppProperties properties = new AppProperties();
		properties.setTrustForwardHeaders(true);
		properties.setTrustedProxyAddresses(List.of("127.0.0.1"));
		ClientIpResolver resolver = new ClientIpResolver(properties);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("198.51.100.20");
		request.addHeader("X-Forwarded-For", "203.0.113.55");

		assertEquals("198.51.100.20", resolver.resolve(request));
	}

	@Test
	void acceptsAnyForwardedProxyWhenWildcardIsConfigured() {
		AppProperties properties = new AppProperties();
		properties.setTrustForwardHeaders(true);
		properties.setTrustedProxyAddresses(List.of("*"));
		ClientIpResolver resolver = new ClientIpResolver(properties);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.10.10.10");
		request.addHeader("X-Forwarded-For", "203.0.113.99");

		assertEquals("203.0.113.99", resolver.resolve(request));
	}
}
