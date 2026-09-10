package com.mytastelog.server.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(String restApiKey, String baseUrl, Duration connectTimeout, Duration readTimeout) {}
