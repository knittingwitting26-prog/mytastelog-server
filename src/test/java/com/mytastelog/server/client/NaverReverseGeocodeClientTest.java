package com.mytastelog.server.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.mytastelog.server.config.NaverMapsProperties;
import com.mytastelog.server.exception.NaverApiException;
import com.mytastelog.server.exception.NaverAuthenticationException;

class NaverReverseGeocodeClientTest {

	private MockRestServiceServer server;
	private NaverReverseGeocodeClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://maps.apigw.ntruss.com");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new NaverReverseGeocodeClient(builder.build(), properties("maps-key-id", "maps-key"));
	}

	@Test
	void sendsOfficialHeadersAndCoordinateParameters() {
		server.expect(requestTo(org.hamcrest.Matchers.containsString("/map-reversegeocode/v2/gc")))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("x-ncp-apigw-api-key-id", "maps-key-id"))
			.andExpect(header("x-ncp-apigw-api-key", "maps-key"))
			.andExpect(queryParam("coords", "126.642,37.533"))
			.andExpect(queryParam("sourcecrs", "epsg:4326"))
			.andExpect(queryParam("orders", "admcode,legalcode,addr,roadaddr"))
			.andExpect(queryParam("output", "json"))
			.andRespond(withSuccess("""
				{"status":{"code":0},"results":[{"name":"admcode","region":{"area1":{"name":"인천광역시"},"area2":{"name":"서구"},"area3":{"name":"청라동"},"area4":{"name":""}}}]}
				""", MediaType.APPLICATION_JSON));

		var response = client.reverseGeocode(new java.math.BigDecimal("37.533"), new java.math.BigDecimal("126.642"));

		assertThat(response.results()).hasSize(1);
		server.verify();
	}

	@Test
	void mapsAuthenticationErrorSeparately() {
		server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> client.reverseGeocode(decimal("37.533"), decimal("126.642")))
			.isInstanceOf(NaverAuthenticationException.class);
	}

	@Test
	void rejectsMissingCredentialsBeforeCallingExternalApi() {
		var clientWithoutCredentials = new NaverReverseGeocodeClient(
			RestClient.create("https://maps.apigw.ntruss.com"),
			properties("", "")
		);

		assertThatThrownBy(() -> clientWithoutCredentials.reverseGeocode(decimal("37.533"), decimal("126.642")))
			.isInstanceOf(NaverAuthenticationException.class)
			.hasMessage("네이버 지도 API 인증 정보가 설정되지 않았습니다.");
	}

	@Test
	void mapsTimeoutToNaverApiError() {
		server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
			.andRespond(withException(new SocketTimeoutException("timed out")));

		assertThatThrownBy(() -> client.reverseGeocode(decimal("37.533"), decimal("126.642")))
			.isInstanceOf(NaverApiException.class)
			.hasMessage("네이버 지도 Reverse Geocoding API 호출 중 오류가 발생했습니다.");
	}

	private java.math.BigDecimal decimal(String value) {
		return new java.math.BigDecimal(value);
	}

	private NaverMapsProperties properties(String apiKeyId, String apiKey) {
		return new NaverMapsProperties(
			apiKeyId,
			apiKey,
			"https://maps.apigw.ntruss.com",
			Duration.ofSeconds(3),
			Duration.ofSeconds(5)
		);
	}
}
