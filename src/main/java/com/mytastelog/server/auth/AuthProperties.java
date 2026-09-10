package com.mytastelog.server.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(String frontendSuccessUrl, String frontendFailureUrl, Google google, Kakao kakao,
	Naver naver) {
	public record Google(String clientId, String clientSecret) {}
	public record Kakao(String clientId, String clientSecret) {}
	public record Naver(String clientId, String clientSecret) {}
}
