package com.mytastelog.server.auth;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import com.mytastelog.server.account.AuthProvider;

@Component
public class KakaoOAuthIdentityExtractor implements OAuthIdentityExtractor {
	@Override
	public boolean supports(String registrationId) {
		return "kakao".equals(registrationId);
	}

	@Override
	public OAuthIdentity extract(OAuth2AuthenticationToken authentication) {
		String subject = authentication.getPrincipal() instanceof OidcUser user
			? user.getSubject()
			: authentication.getPrincipal().getAttribute("sub");
		if (subject == null || subject.isBlank()) {
			throw new OAuth2AuthenticationException(
				new OAuth2Error("invalid_authentication_response"), "Kakao subject is missing");
		}
		return new OAuthIdentity(AuthProvider.KAKAO, subject);
	}
}
