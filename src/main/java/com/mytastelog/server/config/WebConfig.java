package com.mytastelog.server.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {
	private final CorsProperties corsProperties;

	public WebConfig(CorsProperties corsProperties) {
		this.corsProperties = corsProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		List<String> origins = corsProperties.allowedOrigins();
		registry.addMapping("/api/v1/**")
			.allowedOrigins(origins.toArray(String[]::new))
			.allowedMethods(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.PUT.name(), HttpMethod.OPTIONS.name())
			.allowedHeaders(HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE, "X-CSRF-TOKEN")
			.allowCredentials(true)
			.maxAge(3600);
	}

}
