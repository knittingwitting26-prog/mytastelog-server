package com.mytastelog.server.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
	"naver.local.client-id=test-client-id",
	"naver.local.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
class WebConfigTest {

	private static final String PRIVATE_APP_ORIGIN =
		"https://mytastelog.private-apps.tossmini.com";
	private static final String PRODUCTION_APP_ORIGIN =
		"https://mytastelog.apps.tossmini.com";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void allowsPrivateAppOrigin() throws Exception {
		mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, PRIVATE_APP_ORIGIN))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PRIVATE_APP_ORIGIN));
	}

	@Test
	void allowsProductionAppOrigin() throws Exception {
		mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, PRODUCTION_APP_ORIGIN))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PRODUCTION_APP_ORIGIN));
	}

	@Test
	void rejectsUnlistedOrigin() throws Exception {
		mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, "https://example.com"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void allowsGetPreflightFromPrivateAppOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/health")
				.header(HttpHeaders.ORIGIN, PRIVATE_APP_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.ACCEPT))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PRIVATE_APP_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PATCH,DELETE,PUT,OPTIONS"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, HttpHeaders.ACCEPT));
	}

}
