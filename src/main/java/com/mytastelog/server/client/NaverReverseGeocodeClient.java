package com.mytastelog.server.client;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.mytastelog.server.config.NaverMapsProperties;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse;
import com.mytastelog.server.exception.NaverApiException;
import com.mytastelog.server.exception.NaverAuthenticationException;

@Component
public class NaverReverseGeocodeClient {

	private static final String ORDERS = "admcode,legalcode,addr,roadaddr";

	private final RestClient restClient;
	private final NaverMapsProperties properties;

	public NaverReverseGeocodeClient(
		@Qualifier("naverMapsRestClient") RestClient restClient,
		NaverMapsProperties properties
	) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public NaverReverseGeocodeResponse reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
		validateCredentials();
		try {
			NaverReverseGeocodeResponse response = restClient.get()
				.uri(uriBuilder -> uriBuilder
					.path("/map-reversegeocode/v2/gc")
					.queryParam("coords", longitude.toPlainString() + "," + latitude.toPlainString())
					.queryParam("sourcecrs", "epsg:4326")
					.queryParam("orders", ORDERS)
					.queryParam("output", "json")
					.build())
				.header("x-ncp-apigw-api-key-id", properties.apiKeyId())
				.header("x-ncp-apigw-api-key", properties.apiKey())
				.retrieve()
				.onStatus(this::isAuthenticationFailure, (request, apiResponse) -> {
					throw new NaverAuthenticationException("네이버 지도 API 인증에 실패했습니다.");
				})
				.onStatus(HttpStatusCode::isError, (request, apiResponse) -> {
					throw new NaverApiException("네이버 지도 Reverse Geocoding API 호출에 실패했습니다.");
				})
				.body(NaverReverseGeocodeResponse.class);

			if (response == null || response.results() == null) {
				throw new NaverApiException("네이버 지도 Reverse Geocoding API 응답 형식이 올바르지 않습니다.");
			}
			return response;
		} catch (NaverAuthenticationException | NaverApiException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new NaverApiException("네이버 지도 Reverse Geocoding API 호출 중 오류가 발생했습니다.", exception);
		}
	}

	private void validateCredentials() {
		if (properties.apiKeyId() == null || properties.apiKeyId().isBlank()
			|| properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new NaverAuthenticationException("네이버 지도 API 인증 정보가 설정되지 않았습니다.");
		}
	}

	private boolean isAuthenticationFailure(HttpStatusCode status) {
		return status.value() == 401 || status.value() == 403;
	}
}
