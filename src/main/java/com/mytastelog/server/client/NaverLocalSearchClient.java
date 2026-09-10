package com.mytastelog.server.client;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.mytastelog.server.dto.NaverLocalSearchResponse;
import com.mytastelog.server.exception.NaverApiException;
import com.mytastelog.server.exception.NaverAuthenticationException;

@Component
public class NaverLocalSearchClient {

	private static final int DISPLAY = 5;
	private static final String SORT = "comment";

	private final RestClient restClient;

	public NaverLocalSearchClient(RestClient naverLocalRestClient) {
		this.restClient = naverLocalRestClient;
	}

	public NaverLocalSearchResponse search(String query) {
		try {
			NaverLocalSearchResponse response = restClient.get()
				.uri(uriBuilder -> uriBuilder
					.path("/v1/search/local.json")
					.queryParam("query", query)
					.queryParam("display", DISPLAY)
					.queryParam("sort", SORT)
					.build())
				.retrieve()
				.onStatus(this::isAuthenticationFailure, (request, apiResponse) -> {
					throw new NaverAuthenticationException();
				})
				.onStatus(HttpStatusCode::isError, (request, apiResponse) -> {
					throw new NaverApiException("네이버 장소 검색 API 호출에 실패했습니다.");
				})
				.body(NaverLocalSearchResponse.class);

			if (response == null || response.items() == null) {
				throw new NaverApiException("네이버 장소 검색 API 응답 형식이 올바르지 않습니다.");
			}
			return response;
		} catch (NaverAuthenticationException | NaverApiException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new NaverApiException("네이버 장소 검색 API 호출 중 오류가 발생했습니다.", exception);
		}
	}

	private boolean isAuthenticationFailure(HttpStatusCode status) {
		return status.value() == 401 || status.value() == 403;
	}
}
