package com.mytastelog.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.local")
public record NaverLocalProperties(
	String clientId,
	String clientSecret,
	String baseUrl
) {
}
