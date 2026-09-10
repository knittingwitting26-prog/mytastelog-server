package com.mytastelog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mytastelog.server.account.AccountIdentityRepository;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.account.AuthProvider;
import com.mytastelog.server.account.AuthenticatedAccount;
import com.mytastelog.server.collection.CollectionItemRepository;
import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.wishlist.WishlistRepository;

@SpringBootTest(properties = {
	"naver.local.client-id=test-client-id",
	"naver.local.client-secret=test-client-secret",
	"app.auth.google.client-id=test-google-client",
	"app.auth.google.client-secret=test-google-secret",
	"app.auth.kakao.client-id=test-kakao-client",
	"app.auth.kakao.client-secret=test-kakao-secret",
	"app.auth.naver.client-id=test-naver-client",
	"app.auth.naver.client-secret=test-naver-secret"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KakaoOAuthIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired OAuthAuthenticationSuccessHandler successHandler;
	@Autowired AccountRepository accounts;
	@Autowired AccountIdentityRepository identities;
	@Autowired DiaryRepository diaries;
	@Autowired RecordRepository records;
	@Autowired WishlistRepository wishlist;
	@Autowired CollectionRepository collections;
	@Autowired CollectionItemRepository collectionItems;

	@BeforeEach
	void cleanDatabase() {
		collectionItems.deleteAll();
		collections.deleteAll();
		records.deleteAll();
		wishlist.deleteAll();
		diaries.deleteAll();
		identities.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void googleKakaoAndNaverAuthorizationStartsCoexist() throws Exception {
		mvc.perform(get("/oauth2/authorization/google"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", startsWith("https://accounts.google.com/o/oauth2/v2/auth?")));

		mvc.perform(get("/oauth2/authorization/kakao"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", allOf(
				startsWith("https://kauth.kakao.com/oauth/authorize?"),
				containsString("client_id=test-kakao-client"),
				containsString("response_type=code"),
				containsString("scope=openid"),
				containsString("state="),
				containsString("nonce="),
				containsString("login/oauth2/code/kakao"))));

		String naverLocation = mvc.perform(get("/oauth2/authorization/naver"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", allOf(
				startsWith("https://nid.naver.com/oauth2.0/authorize?"),
				containsString("client_id=test-naver-client"),
				containsString("response_type=code"),
				containsString("state="),
				containsString("login/oauth2/code/naver"))))
			.andReturn().getResponse().getRedirectedUrl();
		assertThat(naverLocation).doesNotContain("scope=", "nonce=");
	}

	@Test
	void kakaoSubBecomesOnlyInternalAccountPrincipal() throws Exception {
		MockHttpSession session = authenticate("kakao", "kakao-subject-principal");
		AuthenticatedAccount principal = principal(session);

		assertThat(principal.getAttributes()).containsOnlyKeys("accountId");
		assertThat(identities.findByProviderAndProviderSubject(AuthProvider.KAKAO, "kakao-subject-principal"))
			.get().extracting(identity -> identity.getAccount().getId()).isEqualTo(principal.accountId());
		mvc.perform(get("/api/v1/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(principal.accountId()))
			.andExpect(jsonPath("$.data.provider").doesNotExist());
	}

	@Test
	void kakaoSessionCanUseArchiveAndForeignAccountGetsNotFound() throws Exception {
		MockHttpSession ownerSession = authenticate("kakao", "kakao-owner-a");
		String diary = "{\"id\":\"kakao-diary\",\"name\":\"Kakao Diary\",\"theme\":\"notebook\"}";
		String record = """
			{"id":"kakao-record","diaryId":"kakao-diary","type":"record","placeId":"place",
			"placeName":"Place","category":"한식","date":"2026년 9월 8일","memo":"memo",
			"address":"서울","visibility":"private","visitAt":"2026-09-08T03:00:00Z"}
			""";

		mvc.perform(post("/api/v1/diaries").session(ownerSession).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(diary))
			.andExpect(status().isCreated());
		mvc.perform(post("/api/v1/records").session(ownerSession).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(record))
			.andExpect(status().isCreated());
		mvc.perform(get("/api/v1/archive").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.records[0].id").value("kakao-record"));

		MockHttpSession foreignSession = authenticate("kakao", "kakao-owner-b");
		mvc.perform(patch("/api/v1/records/kakao-record").session(foreignSession).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"attack\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void kakaoLogoutInvalidatesSessionAndPreservesAccountAndArchive() throws Exception {
		MockHttpSession session = authenticate("kakao", "kakao-logout");
		AuthenticatedAccount principal = principal(session);
		mvc.perform(post("/api/v1/diaries").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"id\":\"logout-diary\",\"name\":\"Preserved\",\"theme\":\"notebook\"}"))
			.andExpect(status().isCreated());

		mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
			.andExpect(status().isNoContent());
		assertThat(session.isInvalid()).isTrue();
		mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
		assertThat(accounts.findById(principal.accountId())).isPresent();
		assertThat(diaries.findByIdAndOwner_Id("logout-diary", principal.accountId())).isPresent();
	}

	private MockHttpSession authenticate(String registrationId, String subject) throws Exception {
		Instant now = Instant.now();
		OidcIdToken idToken = new OidcIdToken("test-id-token", now, now.plusSeconds(300),
			Map.of("sub", subject, "email", "same@example.com"));
		var oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken, "sub");
		var oauth = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), registrationId);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		successHandler.onAuthenticationSuccess(request, response, oauth);
		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
		return (MockHttpSession) request.getSession(false);
	}

	private AuthenticatedAccount principal(MockHttpSession session) {
		SecurityContext context = (SecurityContext) session.getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		return (AuthenticatedAccount) context.getAuthentication().getPrincipal();
	}
}
