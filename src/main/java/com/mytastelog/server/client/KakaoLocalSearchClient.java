package com.mytastelog.server.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.mytastelog.server.dto.KakaoLocalSearchResponse;
import com.mytastelog.server.exception.KakaoApiException;

@Component
public class KakaoLocalSearchClient {
	private final RestClient restClient;

	public KakaoLocalSearchClient(@Qualifier("kakaoLocalRestClient") RestClient restClient) { this.restClient = restClient; }

	public KakaoLocalSearchResponse search(String query) {
		try {
			var response = restClient.get().uri(uriBuilder -> uriBuilder
				.path("/v2/local/search/keyword.json").queryParam("query", query).queryParam("size", 10).build())
				.retrieve().onStatus(HttpStatusCode::isError, (request, apiResponse) -> {
					throw new KakaoApiException("카카오 장소 검색 API 호출에 실패했습니다.");
				}).body(KakaoLocalSearchResponse.class);
			if (response == null || response.documents() == null) throw new KakaoApiException("카카오 장소 검색 응답 형식이 올바르지 않습니다.");
			return response;
		} catch (KakaoApiException exception) { throw exception; }
		catch (RestClientException exception) { throw new KakaoApiException("카카오 장소 검색 API 호출 중 오류가 발생했습니다.", exception); }
	}
}
