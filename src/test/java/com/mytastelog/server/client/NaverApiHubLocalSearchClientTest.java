package com.mytastelog.server.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;
import com.mytastelog.server.config.NaverApiHubLocalProperties;
import com.mytastelog.server.exception.NaverApiException;
import com.mytastelog.server.exception.NaverAuthenticationException;

class NaverApiHubLocalSearchClientTest {
	@Test void sendsApiHubEndpointHeadersAndParameters() {
		var builder = RestClient.builder().baseUrl("https://naverapihub.apigw.ntruss.com")
			.defaultHeader("X-NCP-APIGW-API-KEY-ID", "test-id").defaultHeader("X-NCP-APIGW-API-KEY", "test-secret");
		var server = MockRestServiceServer.bindTo(builder).build();
		var client = new NaverApiHubLocalSearchClient(builder.build(), properties("test-id", "test-secret"), new ObjectMapper());
		server.expect(requestTo("https://naverapihub.apigw.ntruss.com/search/v1/local?query=%EC%B9%B4%ED%8E%98&display=5&start=1&sort=comment&format=json"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("X-NCP-APIGW-API-KEY-ID", "test-id")).andExpect(header("X-NCP-APIGW-API-KEY", "test-secret"))
			.andExpect(queryParam("display", "5")).andExpect(queryParam("start", "1"))
			.andExpect(queryParam("sort", "comment")).andExpect(queryParam("format", "json"))
			.andRespond(withSuccess("""
				{"items":[{"title":"<b>스타벅스</b> 한국프레스센터점","category":"카페,디저트>카페",
				"address":"지번 주소","roadAddress":"도로명 주소","mapx":"1269780493","mapy":"375672475"}]}
				""", MediaType.TEXT_PLAIN));
		var item = client.search("카페").items().getFirst();
		assertThat(item.mapx()).isEqualTo("1269780493");
		assertThat(item.mapy()).isEqualTo("375672475");
		server.verify();
	}

	@Test void rejectsMissingCredentialWithoutRequest() {
		var client = new NaverApiHubLocalSearchClient(RestClient.create(), properties("", ""), new ObjectMapper());
		assertThatThrownBy(() -> client.search("카페")).isInstanceOf(NaverAuthenticationException.class);
	}

	@Test void mapsAuthenticationAndProviderFailures() {
		assertFailure(HttpStatus.UNAUTHORIZED, NaverAuthenticationException.class);
		assertFailure(HttpStatus.TOO_MANY_REQUESTS, NaverApiException.class);
		assertFailure(HttpStatus.INTERNAL_SERVER_ERROR, NaverApiException.class);
	}

	private void assertFailure(HttpStatus status, Class<? extends Throwable> expected) {
		var builder = RestClient.builder().baseUrl("https://naverapihub.apigw.ntruss.com");
		var server = MockRestServiceServer.bindTo(builder).build();
		var client = new NaverApiHubLocalSearchClient(builder.build(), properties("id", "secret"), new ObjectMapper());
		server.expect(requestTo(org.hamcrest.Matchers.any(String.class))).andRespond(withStatus(status));
		assertThatThrownBy(() -> client.search("카페")).isInstanceOf(expected);
	}

	private NaverApiHubLocalProperties properties(String id, String secret) {
		return new NaverApiHubLocalProperties(id, secret, "https://naverapihub.apigw.ntruss.com", Duration.ofSeconds(3), Duration.ofSeconds(5));
	}
}
