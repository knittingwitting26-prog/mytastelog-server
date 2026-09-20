package com.mytastelog.server.archive.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.record.RecordVisibility;

public final class ArchiveResponses {
	private ArchiveResponses() {
	}

	public record ApiSuccess<T>(T data) {}

	public record DiaryResponse(
		String id, String ownerId, String name, DiaryTheme theme, Instant createdAt, Instant updatedAt
	) {}

	public record RecordMenuResponse(String id, String name, Long price, int position) {}

	public record RecordResponse(
		String id, String ownerId, String diaryId, String placeId, String placeName, String category,
		String date, String memo, String address, BigDecimal latitude, BigDecimal longitude,
		BigDecimal rating, String menu, Long price,
		String note, String photo, Instant createdAt, Instant updatedAt, String type,
		RecordVisibility visibility, Instant visitAt, List<RecordMenuResponse> menus
	) {}

	public record WishlistResponse(
		String id, String ownerId, String diaryId, String placeId, String placeName, String category,
		String date, String memo, String address, BigDecimal latitude, BigDecimal longitude,
		BigDecimal rating, String menu, Long price,
		String note, String photo, Instant createdAt, Instant updatedAt, String type
	) {}

	public record CollectionResponse(
		String id, String ownerId, String diaryId, String name, String memo, List<String> itemIds,
		String photo, Instant createdAt, Instant updatedAt
	) {}

	public record RevisitIntentResponse(
		String id, String ownerId, String diaryId, String placeId, Instant createdAt
	) {}

	public record ArchiveResponse(
		String apiVersion, Instant serverTime, List<DiaryResponse> diaries, List<RecordResponse> records,
		List<WishlistResponse> wishlist, List<CollectionResponse> collections, List<RevisitIntentResponse> revisitIntents
	) {}

	public record ConvertWishlistResponse(
		RecordResponse record, List<CollectionResponse> collections
	) {}
}
