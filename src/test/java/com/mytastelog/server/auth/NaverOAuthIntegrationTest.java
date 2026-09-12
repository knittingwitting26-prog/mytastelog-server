package com.mytastelog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
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
	"app.auth.google.client-id=test-google-client",
	"app.auth.google.client-secret=test-google-secret",
	"app.auth.kakao.client-id=test-kakao-client",
	"app.auth.kakao.client-secret=test-kakao-secret",
	"app.auth.naver.client-id=test-naver-client",
	"app.auth.naver.client-secret=test-naver-secret"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NaverOAuthIntegrationTest {
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
	void naverIdBecomesOnlyInternalAccountPrincipal() throws Exception {
		MockHttpSession session = authenticateNaver("N1");
		AuthenticatedAccount principal = principal(session);

		assertThat(principal.getAttributes()).containsOnlyKeys("accountId");
		assertThat(identities.findByProviderAndProviderSubject(AuthProvider.NAVER, "N1"))
			.get().extracting(identity -> identity.getAccount().getId()).isEqualTo(principal.accountId());
		mvc.perform(get("/api/v1/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(principal.accountId()))
			.andExpect(jsonPath("$.data.provider").doesNotExist());
	}

	@Test
	void sameEmailAcrossAllProvidersDoesNotMergeAccounts() throws Exception {
		String google = principal(authenticateOidc("google", "G1")).accountId();
		String kakao = principal(authenticateOidc("kakao", "K1")).accountId();
		String naver = principal(authenticateNaver("N1")).accountId();

		assertThat(List.of(google, kakao, naver)).doesNotHaveDuplicates();
		assertThat(accounts.count()).isEqualTo(3);
		assertThat(identities.count()).isEqualTo(3);
	}

	@Test
	void naverSessionCanUseArchiveAndForeignAccountGetsNotFound() throws Exception {
		MockHttpSession ownerSession = authenticateNaver("naver-owner-a");
		mvc.perform(post("/api/v1/diaries").session(ownerSession).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"id\":\"naver-diary\",\"name\":\"Naver Diary\",\"theme\":\"notebook\"}"))
			.andExpect(status().isCreated());
		mvc.perform(post("/api/v1/records").session(ownerSession).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("""
				{"id":"naver-record","diaryId":"naver-diary","type":"record","placeId":"place",
				"placeName":"Place","category":"한식","date":"2026년 9월 9일","memo":"memo",
				"address":"서울","visibility":"private","visitAt":"2026-09-09T03:00:00Z"}
				"""))
			.andExpect(status().isCreated());
		mvc.perform(get("/api/v1/archive").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.records[0].id").value("naver-record"));

		MockHttpSession foreignSession = authenticateNaver("naver-owner-b");
		mvc.perform(patch("/api/v1/records/naver-record").session(foreignSession).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"attack\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void naverLogoutInvalidatesSessionAndPreservesAccountAndArchive() throws Exception {
		MockHttpSession session = authenticateNaver("naver-logout");
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

		MockHttpSession relogin = authenticateNaver("naver-logout");
		assertThat(principal(relogin).accountId()).isEqualTo(principal.accountId());
		assertThat(accounts.count()).isEqualTo(1);
		assertThat(identities.count()).isEqualTo(1);
	}

	private MockHttpSession authenticateNaver(String subject) throws Exception {
		var authorities = List.of(new SimpleGrantedAuthority("OAUTH2_USER"));
		var user = new DefaultOAuth2User(authorities,
			Map.of("id", subject, "email", "same@example.com"), "id");
		return authenticate(new OAuth2AuthenticationToken(user, authorities, "naver"));
	}

	private MockHttpSession authenticateOidc(String registrationId, String subject) throws Exception {
		Instant now = Instant.now();
		OidcIdToken token = new OidcIdToken("test-id-token", now, now.plusSeconds(300),
			Map.of("sub", subject, "email", "same@example.com"));
		var authorities = List.of(new SimpleGrantedAuthority("OIDC_USER"));
		var user = new DefaultOidcUser(authorities, token, "sub");
		return authenticate(new OAuth2AuthenticationToken(user, authorities, registrationId));
	}

	private MockHttpSession authenticate(OAuth2AuthenticationToken authentication) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		successHandler.onAuthenticationSuccess(request, response, authentication);
		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
		return (MockHttpSession) request.getSession(false);
	}

	private AuthenticatedAccount principal(MockHttpSession session) {
		SecurityContext context = (SecurityContext) session.getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		return (AuthenticatedAccount) context.getAuthentication().getPrincipal();
	}
}
