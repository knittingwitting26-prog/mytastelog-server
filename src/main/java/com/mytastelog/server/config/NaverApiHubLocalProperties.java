package com.mytastelog.server.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.api-hub.local")
public record NaverApiHubLocalProperties(String clientId, String clientSecret, String baseUrl,
	Duration connectTimeout, Duration readTimeout) {}
