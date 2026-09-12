package com.mytastelog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.mytastelog.server.account.AuthProvider;

class GoogleOAuthIdentityExtractorTest {
	private final GoogleOAuthIdentityExtractor extractor = new GoogleOAuthIdentityExtractor();

	@Test
	void extractsOnlyStableSubAndIgnoresEmailAndProfileAttributes() {
		var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OIDC_USER")),
			Map.of("sub", "stable-google-sub", "email", "ignored@example.com", "name", "Ignored"), "sub");
		var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");

		assertThat(extractor.extract(authentication))
			.isEqualTo(new OAuthIdentity(AuthProvider.GOOGLE, "stable-google-sub"));
	}

	@Test
	void rejectsResponseWithoutSubInsteadOfFallingBackToEmail() {
		var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OIDC_USER")),
			Map.of("email", "not-an-identity@example.com"), "email");
		var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");

		assertThatThrownBy(() -> extractor.extract(authentication))
			.isInstanceOf(OAuth2AuthenticationException.class);
	}
}
