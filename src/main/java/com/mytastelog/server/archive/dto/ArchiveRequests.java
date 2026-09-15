package com.mytastelog.server.archive.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.record.RecordVisibility;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class ArchiveRequests {
	private ArchiveRequests() {
	}

	public record CreateDiaryRequest(
		@NotBlank @Size(max = 128) String id,
		@NotBlank String name,
		@NotNull DiaryTheme theme
	) {}

	public static final class UpdateDiaryRequest {
		private String name;
		private DiaryTheme theme;
		private boolean namePresent;
		private boolean themePresent;

		@JsonSetter public void setName(String name) { this.name = name; this.namePresent = true; }
		@JsonSetter public void setTheme(DiaryTheme theme) { this.theme = theme; this.themePresent = true; }
		public String name() { return name; }
		public DiaryTheme theme() { return theme; }
		public boolean namePresent() { return namePresent; }
		public boolean themePresent() { return themePresent; }
	}

	public record CreateRecordRequest(
		@NotBlank @Size(max = 128) String id,
		@NotBlank @Size(max = 128) String diaryId,
		@NotBlank String type,
		@NotBlank String placeId,
		@NotBlank String placeName,
		@NotBlank String category,
		@NotBlank String date,
		@NotNull String memo,
		@NotBlank String address,
		@DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
		@DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
		@NotNull RecordVisibility visibility,
		@NotNull Instant visitAt,
		@DecimalMin("0.0") @DecimalMax("5.0") BigDecimal rating,
		String menu,
		@PositiveOrZero Long price,
		String note,
		String photo
	) {
		public CreateRecordRequest(String id, String diaryId, String type, String placeId, String placeName,
			String category, String date, String memo, String address, RecordVisibility visibility, Instant visitAt,
			BigDecimal rating, String menu, Long price, String note, String photo) {
			this(id, diaryId, type, placeId, placeName, category, date, memo, address, null, null,
				visibility, visitAt, rating, menu, price, note, photo);
		}
	}

	public static final class UpdateRecordRequest {
		private String placeName;
		private String memo;
		private RecordVisibility visibility;
		private Instant visitAt;
		private BigDecimal rating;
		private String menu;
		private Long price;
		private boolean placeNamePresent;
		private boolean memoPresent;
		private boolean visibilityPresent;
		private boolean visitAtPresent;
		private boolean ratingPresent;
		private boolean menuPresent;
		private boolean pricePresent;

		@JsonSetter public void setPlaceName(String value) { placeName = value; placeNamePresent = true; }
		@JsonSetter public void setMemo(String value) { memo = value; memoPresent = true; }
		@JsonSetter public void setVisibility(RecordVisibility value) { visibility = value; visibilityPresent = true; }
		@JsonSetter public void setVisitAt(Instant value) { visitAt = value; visitAtPresent = true; }
		@JsonSetter public void setRating(BigDecimal value) { rating = value; ratingPresent = true; }
		@JsonSetter public void setMenu(String value) { menu = value; menuPresent = true; }
		@JsonSetter public void setPrice(Long value) { price = value; pricePresent = true; }
		public String placeName() { return placeName; }
		public String memo() { return memo; }
		public RecordVisibility visibility() { return visibility; }
		public Instant visitAt() { return visitAt; }
		public BigDecimal rating() { return rating; }
		public String menu() { return menu; }
		public Long price() { return price; }
		public boolean placeNamePresent() { return placeNamePresent; }
		public boolean memoPresent() { return memoPresent; }
		public boolean visibilityPresent() { return visibilityPresent; }
		public boolean visitAtPresent() { return visitAtPresent; }
		public boolean ratingPresent() { return ratingPresent; }
		public boolean menuPresent() { return menuPresent; }
		public boolean pricePresent() { return pricePresent; }
	}

	public record CreateWishlistRequest(
		@NotBlank @Size(max = 128) String id,
		@NotBlank @Size(max = 128) String diaryId,
		@NotBlank String type,
		@NotBlank String placeId,
		@NotBlank String placeName,
		@NotBlank String category,
		@NotBlank String date,
		@NotNull String memo,
		@NotBlank String address,
		@DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
		@DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
		@DecimalMin("0.0") @DecimalMax("5.0") BigDecimal rating,
		String menu,
		@PositiveOrZero Long price,
		String note,
		String photo
	) {
		public CreateWishlistRequest(String id, String diaryId, String type, String placeId, String placeName,
			String category, String date, String memo, String address, BigDecimal rating, String menu, Long price,
			String note, String photo) {
			this(id, diaryId, type, placeId, placeName, category, date, memo, address, null, null,
				rating, menu, price, note, photo);
		}
	}

	public record CreateRevisitIntentRequest(
		@NotBlank @Size(max = 128) String id,
		@NotBlank @Size(max = 128) String diaryId,
		@NotBlank String placeId
	) {}

	public static final class UpdateWishlistRequest {
		private String placeName;
		private String memo;
		private boolean placeNamePresent;
		private boolean memoPresent;

		@JsonSetter public void setPlaceName(String value) { placeName = value; placeNamePresent = true; }
		@JsonSetter public void setMemo(String value) { memo = value; memoPresent = true; }
		public String placeName() { return placeName; }
		public String memo() { return memo; }
		public boolean placeNamePresent() { return placeNamePresent; }
		public boolean memoPresent() { return memoPresent; }
	}

	public record ConvertWishlistRequest(
		@NotBlank @Size(max = 128) String id,
		@NotBlank String placeId,
		@NotBlank String placeName,
		@NotBlank String category,
		@NotBlank String date,
		@NotNull String memo,
		@NotBlank String address,
		@DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
		@DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
		@NotNull RecordVisibility visibility,
		@NotNull Instant visitAt,
		@DecimalMin("0.0") @DecimalMax("5.0") BigDecimal rating,
		String menu,
		@PositiveOrZero Long price,
		String note,
		String photo
	) {
		public ConvertWishlistRequest(String id, String placeId, String placeName, String category, String date,
			String memo, String address, RecordVisibility visibility, Instant visitAt, BigDecimal rating,
			String menu, Long price, String note, String photo) {
			this(id, placeId, placeName, category, date, memo, address, null, null, visibility, visitAt,
				rating, menu, price, note, photo);
		}
	}

	public record CreateCollectionRequest(
		@NotBlank @Size(max = 128) String id,
		@NotBlank @Size(max = 128) String diaryId,
		@NotBlank String name,
		@NotNull String memo,
		@NotNull List<@NotBlank @Size(max = 128) String> itemIds
	) {}

	public static final class UpdateCollectionRequest {
		private String name;
		private boolean namePresent;
		@JsonSetter public void setName(String value) { name = value; namePresent = true; }
		public String name() { return name; }
		public boolean namePresent() { return namePresent; }
	}

	public record ReplaceCollectionOrderRequest(
		@NotNull List<@NotBlank @Size(max = 128) String> collectionIds
	) {}
}
