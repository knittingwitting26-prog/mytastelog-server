package com.mytastelog.server.publicrecord;

import java.math.BigDecimal;

public final class PublicRecordResponses {
	private PublicRecordResponses() {}

	public record PublicPlaceSummary(
		String recordId, String placeId, String placeName, String address, String category,
		BigDecimal latitude, BigDecimal longitude, BigDecimal averageRating,
		long publicRecordCount, boolean hasPhoto
	) {}

	public record PublicRecordDetail(
		String id, String placeId, String placeName, String address, String category,
		BigDecimal latitude, BigDecimal longitude, BigDecimal rating, String menu,
		String photoUrl
	) {}
}
