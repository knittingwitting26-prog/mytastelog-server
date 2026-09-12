package com.mytastelog.server.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
	"app.auth.google.client-id=",
	"app.auth.google.client-secret=",
	"app.auth.kakao.client-id=",
	"app.auth.kakao.client-secret=",
	"app.auth.naver.client-id=",
	"app.auth.naver.client-secret="
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OAuthUnavailableIntegrationTest {
	@Autowired MockMvc mvc;

	@Test
	void missingGoogleCredentialsRedirectToSafeFrontendFailure() throws Exception {
		mvc.perform(get("/oauth2/authorization/google"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", containsString(
				"authError=SERVER_AUTHENTICATION_FAILED")));
	}

	@Test
	void missingKakaoCredentialsRedirectToSafeFrontendFailure() throws Exception {
		mvc.perform(get("/oauth2/authorization/kakao"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", containsString(
				"authError=SERVER_AUTHENTICATION_FAILED")));
	}

	@Test
	void missingNaverCredentialsRedirectToSafeFrontendFailure() throws Exception {
		mvc.perform(get("/oauth2/authorization/naver"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", containsString(
				"authError=SERVER_AUTHENTICATION_FAILED")));
	}
}
