package com.mytastelog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mytastelog.server.account.AccountIdentityRepository;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.account.AuthenticatedAccount;
import com.mytastelog.server.archive.ArchiveService;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.diary.DiaryTheme;

@SpringBootTest(properties = {
	"app.auth.google.client-id=test-google-client",
	"app.auth.google.client-secret=test-google-secret"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthenticationWebIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired OAuthAuthenticationSuccessHandler successHandler;
	@Autowired OAuthAuthenticationFailureHandler failureHandler;
	@Autowired ArchiveService archiveService;
	@Autowired AccountRepository accounts;
	@Autowired AccountIdentityRepository identities;
	@Autowired DiaryRepository diaries;
	@Autowired Environment environment;

	@BeforeEach
	void cleanDatabase() {
		identities.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void googleSuccessMapsSubjectToInternalPrincipalAndSession() throws Exception {
		MockHttpSession session = authenticate("google-subject-a");
		SecurityContext context = (SecurityContext) session.getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

		assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(AuthenticatedAccount.class);
		AuthenticatedAccount principal = (AuthenticatedAccount) context.getAuthentication().getPrincipal();
		assertThat(principal.accountId()).isEqualTo(accounts.findAll().getFirst().getId());
		assertThat(principal.getAttributes()).containsOnlyKeys("accountId");

		mvc.perform(get("/api/v1/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(principal.accountId()));
	}

	@Test
	void anonymousMeAndMissingCsrfUseContractErrorsWhileValidCsrfAllowsWrite() throws Exception {
		mvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

		MockHttpSession session = authenticate("csrf-subject");
		mvc.perform(post("/api/v1/diaries").session(session)
			.contentType("application/json")
			.content("{\"id\":\"csrf-diary\",\"name\":\"Diary\",\"theme\":\"notebook\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		mvc.perform(post("/api/v1/diaries").session(session).with(csrf())
			.contentType("application/json")
			.content("{\"id\":\"csrf-diary\",\"name\":\"Diary\",\"theme\":\"notebook\"}"))
			.andExpect(status().isCreated());
	}

	@Test
	void csrfEndpointPublishesHeaderTokenWithoutAuthentication() throws Exception {
		mvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
			.andExpect(jsonPath("$.data.parameterName").value("_csrf"))
			.andExpect(jsonPath("$.data.token").isNotEmpty());
	}

	@Test
	void logoutRequiresCsrfInvalidatesSessionAndPreservesArchive() throws Exception {
		MockHttpSession session = authenticate("logout-subject");
		AuthenticatedAccount principal = principal(session);
		archiveService.createDiary(principal.accountId(),
			new CreateDiaryRequest("preserved-diary", "Preserved", DiaryTheme.NOTEBOOK));
		mvc.perform(post("/api/v1/records").session(session).with(csrf())
			.contentType("application/json")
			.content("""
				{"id":"preserved-record","diaryId":"preserved-diary","type":"record","placeId":"place",
				"placeName":"Persistent","category":"food","date":"2026-09-10","memo":"memo",
				"address":"Seoul","visibility":"private","visitAt":"2026-09-10T03:00:00Z"}
				"""))
			.andExpect(status().isCreated());

		mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
		mvc.perform(post("/api/v1/auth/logout").session(session))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge("JSESSIONID", 0));
		assertThat(session.isInvalid()).isTrue();
		mvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized());

		assertThat(diaries.findByIdAndOwner_Id("preserved-diary", principal.accountId())).isPresent();
		MockHttpSession relogin = authenticate("logout-subject");
		assertThat(principal(relogin).accountId()).isEqualTo(principal.accountId());
		assertThat(accounts.count()).isEqualTo(1);
		assertThat(diaries.count()).isEqualTo(1);
		mvc.perform(get("/api/v1/archive").session(relogin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.diaries[0].id").value("preserved-diary"))
			.andExpect(jsonPath("$.data.records[0].id").value("preserved-record"))
			.andExpect(jsonPath("$.data.records[0].ownerId").value(principal.accountId()));
	}

	@Test
	void googleAuthorizationStartUsesStandardEndpointAndRedirectCallback() throws Exception {
		mvc.perform(get("/oauth2/authorization/google"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", allOf(
				startsWith("https://accounts.google.com/o/oauth2/v2/auth?"),
				containsString("scope=openid"),
				containsString("login/oauth2/code/google"))));
	}

	@Test
	void oauthFailureRedirectContainsOnlySafeContractCode() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		failureHandler.onAuthenticationFailure(request, response, new OAuth2AuthenticationException(
			new OAuth2Error("access_denied"), "secret=client-secret&code=authorization-code"));

		assertThat(response.getRedirectedUrl()).contains("authError=PROVIDER_CANCELLED")
			.doesNotContain("client-secret", "authorization-code", "access_denied");
	}

	@Test
	void cookieDefaultsAreHttpOnlyLaxAndTestProfileIsNotSecure() {
		assertThat(environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class)).isTrue();
		assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
		assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)).isFalse();
	}

	private MockHttpSession authenticate(String subject) throws Exception {
		Instant now = Instant.now();
		OidcIdToken idToken = new OidcIdToken("test-id-token", now, now.plusSeconds(300),
			Map.of("sub", subject));
		var oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken, "sub");
		var oauth = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "google");
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
