package com.mytastelog.server.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlaceSearchResponse(List<Place> places) {

	public record Place(
		String placeId,
		String source,
		String providerPlaceId,
		String name,
		String category,
		String address,
		String roadAddress,
		BigDecimal longitude,
		BigDecimal latitude
	) {
	}
}
