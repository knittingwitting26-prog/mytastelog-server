package com.mytastelog.server.record;

import java.math.BigDecimal;
import java.time.Instant;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.common.persistence.PlaceArchiveEntity;
import com.mytastelog.server.diary.DiaryEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "records", indexes = {
	@Index(name = "idx_records_owner_diary", columnList = "owner_id, diary_id"),
	@Index(name = "idx_records_diary_visit", columnList = "diary_id, visit_at")
})
public class RecordEntity extends PlaceArchiveEntity {
	@Convert(converter = RecordVisibilityConverter.class)
	@Column(nullable = false, length = 16)
	private RecordVisibility visibility;

	@Column(name = "visit_at", nullable = false)
	private Instant visitAt;

	protected RecordEntity() {
	}

	public RecordEntity(String id, AccountEntity owner, DiaryEntity diary, String placeId, String placeName,
		String category, String dateDisplay, String memo, String address, BigDecimal rating, String menu,
		Long price, String note, String photoReference, RecordVisibility visibility, Instant visitAt) {
		this(id, owner, diary, placeId, placeName, category, dateDisplay, memo, address, null, null,
			rating, menu, price, note, photoReference, visibility, visitAt);
	}

	public RecordEntity(String id, AccountEntity owner, DiaryEntity diary, String placeId, String placeName,
		String category, String dateDisplay, String memo, String address, BigDecimal latitude,
		BigDecimal longitude, BigDecimal rating, String menu, Long price, String note, String photoReference,
		RecordVisibility visibility, Instant visitAt) {
		super(id, owner, diary, placeId, placeName, category, dateDisplay, memo, address,
			latitude, longitude, rating, menu, price, note, photoReference);
		this.visibility = visibility;
		this.visitAt = visitAt;
	}

	public void update(String placeName, String memo, RecordVisibility visibility, Instant visitAt,
		BigDecimal rating, String menu, Long price) {
		updateEditableFields(placeName, memo, rating, menu, price);
		this.visibility = visibility;
		this.visitAt = visitAt;
	}

	public RecordVisibility getVisibility() { return visibility; }
	public Instant getVisitAt() { return visitAt; }
}
