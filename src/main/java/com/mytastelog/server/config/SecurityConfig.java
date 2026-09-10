package com.mytastelog.server.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.SecurityFilterChain;

import com.mytastelog.server.auth.AuthProperties;
import com.mytastelog.server.auth.OAuthAuthenticationFailureHandler;
import com.mytastelog.server.auth.OAuthAuthenticationSuccessHandler;
import com.mytastelog.server.auth.NaverAuthorizationCodeTokenRequestParametersConverter;
import com.mytastelog.server.auth.NaverOAuth2UserService;
import com.mytastelog.server.auth.SafeOAuth2AuthorizationRequestResolver;
import com.mytastelog.server.dto.ErrorResponse;
import com.mytastelog.server.exception.ApiErrorCode;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
		ObjectProvider<ClientRegistrationRepository> clientRegistrations,
		OAuthAuthenticationSuccessHandler authenticationSuccessHandler,
		OAuthAuthenticationFailureHandler authenticationFailureHandler,
		NaverOAuth2UserService naverOAuth2UserService,
		NaverAuthorizationCodeTokenRequestParametersConverter naverTokenParameters) throws Exception {
		http
			.cors(Customizer.withDefaults())
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/api/v1/health", "/api/v1/places/**", "/api/v1/auth/csrf",
					"/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
				.anyRequest().authenticated())
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
				.sessionFixation(fixation -> fixation.changeSessionId()))
			.requestCache(cache -> cache.requestCache(new NullRequestCache()))
			.httpBasic(httpBasic -> httpBasic.disable())
			.formLogin(formLogin -> formLogin.disable())
			.logout(logout -> logout
				.logoutUrl("/api/v1/auth/logout")
				.invalidateHttpSession(true)
				.clearAuthentication(true)
				.deleteCookies("JSESSIONID")
				.logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) -> writeError(response, objectMapper,
					401, ApiErrorCode.UNAUTHORIZED, "인증이 필요합니다."))
				.accessDeniedHandler((request, response, exception) -> writeError(response, objectMapper,
					403, ApiErrorCode.FORBIDDEN, "요청 권한이 없습니다.")));

		ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
		if (registrations != null) {
			RestClientAuthorizationCodeTokenResponseClient tokenClient =
				new RestClientAuthorizationCodeTokenResponseClient();
			tokenClient.addParametersConverter(naverTokenParameters);
			http.oauth2Login(oauth -> oauth
				.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
					new SafeOAuth2AuthorizationRequestResolver(registrations)))
				.tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenClient))
				.userInfoEndpoint(endpoint -> endpoint.userService(naverOAuth2UserService))
				.successHandler(authenticationSuccessHandler)
				.failureHandler(authenticationFailureHandler));
		}
		return http.build();
	}

	private void writeError(jakarta.servlet.http.HttpServletResponse response, ObjectMapper objectMapper,
		int status, ApiErrorCode code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code.name(), message));
	}
}
