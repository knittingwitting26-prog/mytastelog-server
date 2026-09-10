package com.mytastelog.server.auth;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

public interface OAuthIdentityExtractor {
	boolean supports(String registrationId);
	OAuthIdentity extract(OAuth2AuthenticationToken authentication);
}
