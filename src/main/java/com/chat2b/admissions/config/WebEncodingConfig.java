package com.chat2b.admissions.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebEncodingConfig implements WebMvcConfigurer {

	@Bean
	CharacterEncodingFilter characterEncodingFilter() {
		CharacterEncodingFilter filter = new CharacterEncodingFilter(StandardCharsets.UTF_8.name(), true, true);
		filter.setForceRequestEncoding(true);
		filter.setForceResponseEncoding(true);
		return filter;
	}

	@Override
	public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		for (HttpMessageConverter<?> converter : converters) {
			if (converter instanceof StringHttpMessageConverter stringConverter) {
				stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
			}
			if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
				List<MediaType> mediaTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
				MediaType utf8Json = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);
				if (!mediaTypes.contains(utf8Json)) {
					mediaTypes.add(utf8Json);
				}
				jacksonConverter.setDefaultCharset(StandardCharsets.UTF_8);
				jacksonConverter.setSupportedMediaTypes(mediaTypes);
			}
		}
	}
}
