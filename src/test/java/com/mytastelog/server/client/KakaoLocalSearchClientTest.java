package com.mytastelog.server.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.mytastelog.server.exception.KakaoApiException;

class KakaoLocalSearchClientTest {
	private MockRestServiceServer server;
	private KakaoLocalSearchClient client;

	@BeforeEach void setUp() {
		var builder = RestClient.builder().baseUrl("https://dapi.kakao.com")
			.defaultHeader("Authorization", "KakaoAK test-key");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new KakaoLocalSearchClient(builder.build());
	}

	@Test void sendsAuthorizationAndMapsResponse() {
		server.expect(requestTo(org.hamcrest.Matchers.containsString("/v2/local/search/keyword.json")))
			.andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "KakaoAK test-key"))
			.andRespond(withSuccess("""
				{"documents":[{"id":"123","place_name":"카페","category_name":"음식점 > 카페","address_name":"지번","road_address_name":"도로명","x":"126.1","y":"37.1"}]}
				""", MediaType.APPLICATION_JSON));
		assertThat(client.search("카페").documents().getFirst().id()).isEqualTo("123");
		server.verify();
	}

	@Test void mapsProviderFailure() {
		server.expect(requestTo(org.hamcrest.Matchers.any(String.class))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertThatThrownBy(() -> client.search("카페")).isInstanceOf(KakaoApiException.class);
	}
}
