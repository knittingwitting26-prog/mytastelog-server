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

class NaverOAuthIdentityExtractorTest {
	private final NaverOAuthIdentityExtractor extractor = new NaverOAuthIdentityExtractor();

	@Test
	void normalizedIdBecomesNaverProviderSubject() {
		assertThat(extractor.extract(authentication(Map.of("id", "N1", "email", "ignored@example.com"))))
			.isEqualTo(new OAuthIdentity(AuthProvider.NAVER, "N1"));
	}

	@Test
	void missingBlankOrNonStringIdFailsAuthentication() {
		assertInvalid(Map.of("name", "missing"));
		assertInvalid(Map.of("id", " "));
		assertInvalid(Map.of("id", 123));
	}

	private void assertInvalid(Map<String, Object> attributes) {
		assertThatThrownBy(() -> extractor.extract(authentication(attributes)))
			.isInstanceOf(OAuth2AuthenticationException.class);
	}

	private OAuth2AuthenticationToken authentication(Map<String, Object> attributes) {
		String nameKey = attributes.containsKey("id") ? "id" : "name";
		var authorities = List.of(new SimpleGrantedAuthority("OAUTH2_USER"));
		var user = new DefaultOAuth2User(authorities, attributes, nameKey);
		return new OAuth2AuthenticationToken(user, authorities, "naver");
	}
}
