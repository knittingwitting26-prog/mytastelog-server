package com.mytastelog.server.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
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
	"naver.local.client-id=test-client-id",
	"naver.local.client-secret=test-client-secret",
	"app.auth.google.client-id=test-google-client",
	"app.auth.google.client-secret=test-google-secret",
	"app.auth.kakao.client-id=",
	"app.auth.kakao.client-secret=",
	"app.auth.naver.client-id=",
	"app.auth.naver.client-secret=incomplete-naver-secret"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NaverSecretOnlyOAuthIsolationIntegrationTest {
	@Autowired MockMvc mvc;

	@Test
	void naverSecretWithoutClientIdDoesNotBreakGoogleOrBackend() throws Exception {
		mvc.perform(get("/oauth2/authorization/google"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", startsWith("https://accounts.google.com/o/oauth2/v2/auth?")));
		mvc.perform(get("/oauth2/authorization/naver"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", containsString("authError=SERVER_AUTHENTICATION_FAILED")));
		mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
	}
}
