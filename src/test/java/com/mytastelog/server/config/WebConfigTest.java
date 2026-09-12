package com.mytastelog.server.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.unit.DataSize;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WebConfigTest {

	private static final String PRIVATE_APP_ORIGIN =
		"https://mytastelog.private-apps.tossmini.com";
	private static final String PRODUCTION_APP_ORIGIN =
		"https://mytastelog.apps.tossmini.com";
	private static final String LOCAL_FRONTEND_ORIGIN = "http://localhost:5173";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private Environment environment;

	@Test
	void multipartLimitsMatchTenMegabytePhotoPolicyWithRequestOverhead() {
		assertThat(environment.getProperty(
			"spring.servlet.multipart.max-file-size", DataSize.class)).isEqualTo(DataSize.ofMegabytes(10));
		assertThat(environment.getProperty(
			"spring.servlet.multipart.max-request-size", DataSize.class)).isEqualTo(DataSize.ofMegabytes(11));
	}

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
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void allowsCredentialedMultipartPreflightFromLocalFrontend() throws Exception {
		mockMvc.perform(options("/api/v1/records/record-id/photo")
				.header(HttpHeaders.ORIGIN, LOCAL_FRONTEND_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
					HttpHeaders.CONTENT_TYPE + "," + CsrfContract.HEADER_NAME))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_FRONTEND_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
				containsString(HttpMethod.POST.name())))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
				containsString(CsrfContract.HEADER_NAME)));
	}

	@Test
	void rejectsMultipartPreflightFromUnlistedOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/records/record-id/photo")
				.header(HttpHeaders.ORIGIN, "https://example.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
					HttpHeaders.CONTENT_TYPE + "," + CsrfContract.HEADER_NAME))
			.andExpect(status().isForbidden())
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
