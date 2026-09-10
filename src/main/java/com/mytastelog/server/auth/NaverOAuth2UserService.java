package com.mytastelog.server.auth;

import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/** Validates Naver's nested profile envelope and retains only its app-scoped user id. */
@Component
public class NaverOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
	private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

	public NaverOAuth2UserService() {
		this(new DefaultOAuth2UserService());
	}

	NaverOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
		this.delegate = delegate;
	}

	@Override
	public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
		OAuth2User user = delegate.loadUser(request);
		if (!"naver".equals(request.getClientRegistration().getRegistrationId())) {
			return user;
		}
		return normalize(user);
	}

	OAuth2User normalize(OAuth2User user) {
		Map<String, Object> attributes = user.getAttributes();
		if (!"00".equals(attributes.get("resultcode"))) {
			throw invalidProfile();
		}
		Object responseValue = attributes.get("response");
		if (!(responseValue instanceof Map<?, ?> response)) {
			throw invalidProfile();
		}
		Object idValue = response.get("id");
		if (!(idValue instanceof String id) || id.isBlank()) {
			throw invalidProfile();
		}
		return new DefaultOAuth2User(user.getAuthorities(), Map.of("id", id), "id");
	}

	private OAuth2AuthenticationException invalidProfile() {
		return new OAuth2AuthenticationException(
			new OAuth2Error("invalid_authentication_response"), "Naver profile is invalid");
	}
}
