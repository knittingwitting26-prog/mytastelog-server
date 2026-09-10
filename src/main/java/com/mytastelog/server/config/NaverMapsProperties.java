package com.mytastelog.server.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.maps")
public record NaverMapsProperties(
	String apiKeyId,
	String apiKey,
	String baseUrl,
	Duration connectTimeout,
	Duration readTimeout
) {
}
