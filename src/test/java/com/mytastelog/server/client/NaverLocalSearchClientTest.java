package com.mytastelog.server.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
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

import com.mytastelog.server.exception.NaverAuthenticationException;
import com.mytastelog.server.exception.NaverApiException;

class NaverLocalSearchClientTest {

	private MockRestServiceServer server;
	private NaverLocalSearchClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://openapi.naver.com")
			.defaultHeader("X-Naver-Client-Id", "test-client-id")
			.defaultHeader("X-Naver-Client-Secret", "test-client-secret");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new NaverLocalSearchClient(builder.build());
	}

	@Test
	void sendsRequiredHeadersAndSearchParametersWithoutRealApiCall() {
		server.expect(requestTo("https://openapi.naver.com/v1/search/local.json?query=%EC%8A%A4%ED%83%80%EB%B2%85%EC%8A%A4%20%EC%B2%AD%EB%9D%BC&display=5&sort=comment"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("X-Naver-Client-Id", "test-client-id"))
			.andExpect(header("X-Naver-Client-Secret", "test-client-secret"))
			.andExpect(queryParam("query", "%EC%8A%A4%ED%83%80%EB%B2%85%EC%8A%A4%20%EC%B2%AD%EB%9D%BC"))
			.andExpect(queryParam("display", "5"))
			.andExpect(queryParam("sort", "comment"))
			.andRespond(withSuccess("""
				{
				  "lastBuildDate": "Fri, 17 Jul 2026 17:00:00 +0900",
				  "items": [{
				    "title": "<b>스타벅스</b> 청라점",
				    "link": "https://example.com",
				    "category": "카페,디저트",
				    "address": "인천광역시 서구 청라동",
				    "roadAddress": "인천광역시 서구 청라로",
				    "mapx": "1266420000",
				    "mapy": "375330000"
				  }]
				}
				""", MediaType.APPLICATION_JSON));

		var response = client.search("스타벅스 청라");

		assertThat(response.items()).hasSize(1);
		assertThat(response.items().getFirst().mapx()).isEqualTo("1266420000");
		server.verify();
	}

	@Test
	void mapsUnauthorizedAuthenticationFailureSeparately() {
		server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> client.search("스타벅스"))
			.isInstanceOf(NaverAuthenticationException.class);
		server.verify();
	}

	@Test
	void mapsForbiddenAuthenticationFailureSeparately() {
		server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
			.andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThatThrownBy(() -> client.search("스타벅스"))
			.isInstanceOf(NaverAuthenticationException.class);
		server.verify();
	}

	@Test
	void mapsOtherNaverErrorToApiException() {
		server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatThrownBy(() -> client.search("스타벅스"))
			.isInstanceOf(NaverApiException.class)
			.hasMessage("네이버 장소 검색 API 호출에 실패했습니다.");
		server.verify();
	}

	@Test
	void mapsMalformedJsonResponseToApiException() {
		server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
			.andRespond(withSuccess("{invalid-json", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.search("스타벅스"))
			.isInstanceOf(NaverApiException.class)
			.hasMessage("네이버 장소 검색 API 호출 중 오류가 발생했습니다.");
		server.verify();
	}
}
