package com.mytastelog.server.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoLocalSearchResponse(List<Document> documents) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Document(
		String id,
		@JsonProperty("place_name") String placeName,
		@JsonProperty("category_name") String categoryName,
		@JsonProperty("address_name") String addressName,
		@JsonProperty("road_address_name") String roadAddressName,
		String x,
		String y
	) {}
}
