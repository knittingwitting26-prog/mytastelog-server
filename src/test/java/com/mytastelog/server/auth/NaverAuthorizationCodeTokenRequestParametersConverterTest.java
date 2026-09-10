package com.mytastelog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;

class NaverAuthorizationCodeTokenRequestParametersConverterTest {
	private final NaverAuthorizationCodeTokenRequestParametersConverter converter =
		new NaverAuthorizationCodeTokenRequestParametersConverter();

	@Test
	void addsAuthorizationStateOnlyToNaverTokenExchange() {
		assertThat(converter.convert(grant("naver"))).containsEntry("state", java.util.List.of("state-value"));
		assertThat(converter.convert(grant("google"))).isEmpty();
	}

	private OAuth2AuthorizationCodeGrantRequest grant(String registrationId) {
		String redirectUri = "http://localhost:8080/login/oauth2/code/" + registrationId;
		ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
			.clientId("client-id")
			.clientSecret("client-secret")
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri(redirectUri)
			.authorizationUri("https://provider.example/authorize")
			.tokenUri("https://provider.example/token")
			.userInfoUri("https://provider.example/me")
			.userNameAttributeName("id")
			.build();
		OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
			.authorizationUri("https://provider.example/authorize")
			.clientId("client-id")
			.redirectUri(redirectUri)
			.state("state-value")
			.build();
		OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success("authorization-code")
			.redirectUri(redirectUri)
			.state("state-value")
			.build();
		return new OAuth2AuthorizationCodeGrantRequest(registration,
			new OAuth2AuthorizationExchange(request, response));
	}
}
