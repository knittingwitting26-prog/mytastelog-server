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

class KakaoOAuthIdentityExtractorTest {
	private final KakaoOAuthIdentityExtractor extractor = new KakaoOAuthIdentityExtractor();

	@Test
	void extractsOnlyStableSubAndIgnoresProfileAttributes() {
		var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OIDC_USER")),
			Map.of("sub", "stable-kakao-sub", "email", "ignored@example.com", "nickname", "ignored"), "sub");
		var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "kakao");

		assertThat(extractor.extract(authentication))
			.isEqualTo(new OAuthIdentity(AuthProvider.KAKAO, "stable-kakao-sub"));
	}

	@Test
	void rejectsResponseWithoutSub() {
		var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OIDC_USER")),
			Map.of("email", "not-an-identity@example.com"), "email");
		var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "kakao");

		assertThatThrownBy(() -> extractor.extract(authentication))
			.isInstanceOf(OAuth2AuthenticationException.class)
			.extracting(failure -> ((OAuth2AuthenticationException) failure).getError().getErrorCode())
			.isEqualTo("invalid_authentication_response");
	}
}
