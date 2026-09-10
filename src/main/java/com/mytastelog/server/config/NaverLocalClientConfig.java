package com.mytastelog.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NaverLocalProperties.class)
public class NaverLocalClientConfig {

	@Bean
	RestClient naverLocalRestClient(NaverLocalProperties properties) {
		return RestClient.builder()
			.baseUrl(properties.baseUrl())
			.defaultHeader("X-Naver-Client-Id", properties.clientId())
			.defaultHeader("X-Naver-Client-Secret", properties.clientSecret())
			.build();
	}
}
