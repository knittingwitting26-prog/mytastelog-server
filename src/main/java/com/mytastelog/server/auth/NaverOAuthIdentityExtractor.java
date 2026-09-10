package com.mytastelog.server.auth;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

import com.mytastelog.server.account.AuthProvider;

@Component
public class NaverOAuthIdentityExtractor implements OAuthIdentityExtractor {
	@Override
	public boolean supports(String registrationId) {
		return "naver".equals(registrationId);
	}

	@Override
	public OAuthIdentity extract(OAuth2AuthenticationToken authentication) {
		Object attribute = authentication.getPrincipal().getAttributes().get("id");
		if (!(attribute instanceof String subject) || subject.isBlank()) {
			throw new OAuth2AuthenticationException(
				new OAuth2Error("invalid_authentication_response"), "Naver subject is missing");
		}
		return new OAuthIdentity(AuthProvider.NAVER, subject);
	}
}
