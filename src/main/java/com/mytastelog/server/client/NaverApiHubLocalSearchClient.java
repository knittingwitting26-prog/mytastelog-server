package com.mytastelog.server.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.mytastelog.server.config.NaverApiHubLocalProperties;
import com.mytastelog.server.dto.NaverLocalSearchResponse;
import com.mytastelog.server.exception.NaverApiException;
import com.mytastelog.server.exception.NaverAuthenticationException;

@Component
public class NaverApiHubLocalSearchClient {
	private final RestClient restClient;
	private final NaverApiHubLocalProperties properties;
	private final ObjectMapper objectMapper;

	public NaverApiHubLocalSearchClient(@Qualifier("naverApiHubLocalRestClient") RestClient restClient,
		NaverApiHubLocalProperties properties, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public NaverLocalSearchResponse search(String query) {
		if (properties.clientId() == null || properties.clientId().isBlank()
			|| properties.clientSecret() == null || properties.clientSecret().isBlank()) {
			throw new NaverAuthenticationException("NAVER API HUB 인증 정보가 설정되지 않았습니다.", null, "configuration");
		}
		try {
			String responseBody = restClient.get().uri(uriBuilder -> uriBuilder.path("/search/v1/local")
				.queryParam("query", query).queryParam("display", 5).queryParam("start", 1)
				.queryParam("sort", "random").queryParam("format", "json").build())
				.retrieve().onStatus(status -> status.value() == 401 || status.value() == 403,
					(request, apiResponse) -> { throw new NaverAuthenticationException(
						"NAVER API HUB 인증에 실패했습니다.", apiResponse.getStatusCode().value(), "authentication"); })
				.onStatus(HttpStatusCode::isError,
					(request, apiResponse) -> { throw new NaverApiException("NAVER API HUB 장소 검색 호출에 실패했습니다.",
						apiResponse.getStatusCode().value(), failureCategory(apiResponse.getStatusCode())); })
				.body(String.class);
			var response = responseBody == null ? null
				: objectMapper.readValue(responseBody, NaverLocalSearchResponse.class);
			if (response == null || response.items() == null) throw new NaverApiException(
				"NAVER API HUB 장소 검색 응답 형식이 올바르지 않습니다.", null, "response_mapping");
			return response;
		} catch (NaverAuthenticationException | NaverApiException exception) { throw exception; }
		catch (JacksonException exception) { throw new NaverApiException(
			"NAVER API HUB 장소 검색 응답 JSON 형식이 올바르지 않습니다.", null, "response_mapping", exception); }
		catch (RestClientException exception) { throw new NaverApiException(
			"NAVER API HUB 장소 검색 호출 중 오류가 발생했습니다.", null, "transport_or_deserialization", exception); }
	}

	private String failureCategory(HttpStatusCode status) {
		if (status.value() == 429) return "rate_limit";
		return status.is4xxClientError() ? "provider_request" : "provider_server";
	}
}
