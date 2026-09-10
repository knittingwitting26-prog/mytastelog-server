package com.mytastelog.server.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NaverMapsProperties.class)
public class NaverMapsClientConfig {

	@Bean
	RestClient naverMapsRestClient(NaverMapsProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());

		return RestClient.builder()
			.baseUrl(properties.baseUrl())
			.requestFactory(requestFactory)
			.build();
	}
}
