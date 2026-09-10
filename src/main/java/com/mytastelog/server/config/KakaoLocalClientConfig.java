package com.mytastelog.server.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KakaoLocalProperties.class)
public class KakaoLocalClientConfig {
	@Bean
	RestClient kakaoLocalRestClient(KakaoLocalProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory)
			.defaultHeader("Authorization", "KakaoAK " + properties.restApiKey()).build();
	}
}
