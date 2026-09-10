package com.mytastelog.server.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@Conditional(OAuthCredentialsCondition.class)
public class OAuthClientRegistrationConfiguration {
	@Bean
	ClientRegistrationRepository clientRegistrationRepository(AuthProperties properties) {
		List<ClientRegistration> registrations = new ArrayList<>();
		if (complete(properties.google().clientId(), properties.google().clientSecret())) {
			registrations.add(google(properties));
		}
		if (complete(properties.kakao().clientId(), properties.kakao().clientSecret())) {
			registrations.add(kakao(properties));
		}
		if (complete(properties.naver().clientId(), properties.naver().clientSecret())) {
			registrations.add(naver(properties));
		}
		return new InMemoryClientRegistrationRepository(registrations);
	}

	private ClientRegistration google(AuthProperties properties) {
		return CommonOAuth2Provider.GOOGLE
			.getBuilder("google")
			.clientId(properties.google().clientId())
			.clientSecret(properties.google().clientSecret())
			.scope("openid")
			.userNameAttributeName("sub")
			.clientName("Google")
			.build();
	}

	private ClientRegistration kakao(AuthProperties properties) {
		return ClientRegistration.withRegistrationId("kakao")
			.clientId(properties.kakao().clientId())
			.clientSecret(properties.kakao().clientSecret())
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.scope("openid")
			.authorizationUri("https://kauth.kakao.com/oauth/authorize")
			.tokenUri("https://kauth.kakao.com/oauth/token")
			.userInfoUri("https://kapi.kakao.com/v1/oidc/userinfo")
			.userNameAttributeName("sub")
			.jwkSetUri("https://kauth.kakao.com/.well-known/jwks.json")
			.issuerUri("https://kauth.kakao.com")
			.clientName("Kakao")
			.build();
	}

	private ClientRegistration naver(AuthProperties properties) {
		return ClientRegistration.withRegistrationId("naver")
			.clientId(properties.naver().clientId())
			.clientSecret(properties.naver().clientSecret())
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.authorizationUri("https://nid.naver.com/oauth2.0/authorize")
			.tokenUri("https://nid.naver.com/oauth2.0/token")
			.userInfoUri("https://openapi.naver.com/v1/nid/me")
			.userNameAttributeName("id")
			.clientName("Naver")
			.build();
	}

	private boolean complete(String clientId, String clientSecret) {
		return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
	}
}
