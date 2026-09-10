package com.mytastelog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class NaverOAuth2UserServiceTest {
	private final NaverOAuth2UserService service = new NaverOAuth2UserService();
	private final List<SimpleGrantedAuthority> authorities =
		List.of(new SimpleGrantedAuthority("OAUTH2_USER"));

	@Test
	void extractsOnlyNestedResponseIdAndDiscardsEmailAndProfileData() {
		OAuth2User raw = raw(Map.of(
			"resultcode", "00",
			"message", "success",
			"response", Map.of("id", "N1", "email", "same@example.com", "nickname", "private")));

		OAuth2User normalized = service.normalize(raw);

		assertThat(normalized.getName()).isEqualTo("N1");
		assertThat(normalized.getAttributes()).containsOnly(Map.entry("id", "N1"));
	}

	@Test
	void rejectsUnsuccessfulResultCode() {
		assertInvalid(Map.of("resultcode", "01", "response", Map.of("id", "N1")));
	}

	@Test
	void rejectsMissingResponse() {
		assertInvalid(Map.of("resultcode", "00"));
	}

	@Test
	void rejectsMissingNullOrBlankResponseId() {
		assertInvalid(Map.of("resultcode", "00", "response", Map.of()));

		Map<String, Object> responseWithNull = new HashMap<>();
		responseWithNull.put("id", null);
		assertInvalid(Map.of("resultcode", "00", "response", responseWithNull));

		assertInvalid(Map.of("resultcode", "00", "response", Map.of("id", "   ")));
	}

	@Test
	void rejectsMalformedResponseShape() {
		assertInvalid(Map.of("resultcode", "00", "response", "not-an-object"));
	}

	private void assertInvalid(Map<String, Object> attributes) {
		assertThatThrownBy(() -> service.normalize(raw(attributes)))
			.isInstanceOf(OAuth2AuthenticationException.class)
			.extracting(failure -> ((OAuth2AuthenticationException) failure).getError().getErrorCode())
			.isEqualTo("invalid_authentication_response");
	}

	private OAuth2User raw(Map<String, Object> attributes) {
		return new DefaultOAuth2User(authorities, attributes, attributes.containsKey("resultcode")
			? "resultcode" : attributes.keySet().iterator().next());
	}
}
