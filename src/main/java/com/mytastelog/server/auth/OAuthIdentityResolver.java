package com.mytastelog.server.auth;

import java.util.List;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class OAuthIdentityResolver {
	private final List<OAuthIdentityExtractor> extractors;

	public OAuthIdentityResolver(List<OAuthIdentityExtractor> extractors) {
		this.extractors = extractors;
	}

	public OAuthIdentity resolve(OAuth2AuthenticationToken authentication) {
		return extractors.stream()
			.filter(extractor -> extractor.supports(authentication.getAuthorizedClientRegistrationId()))
			.findFirst()
			.orElseThrow(() -> new AuthenticationServiceException("Unsupported OAuth provider"))
			.extract(authentication);
	}
}
