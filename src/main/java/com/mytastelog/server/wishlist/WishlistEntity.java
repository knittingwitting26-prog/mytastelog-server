package com.mytastelog.server.wishlist;

import java.math.BigDecimal;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.common.persistence.PlaceArchiveEntity;
import com.mytastelog.server.diary.DiaryEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "wishlist", indexes = @Index(name = "idx_wishlist_owner_diary", columnList = "owner_id, diary_id"))
public class WishlistEntity extends PlaceArchiveEntity {
	protected WishlistEntity() {
	}

	public WishlistEntity(String id, AccountEntity owner, DiaryEntity diary, String placeId, String placeName,
		String category, String dateDisplay, String memo, String address, BigDecimal rating, String menu,
		Long price, String note, String photoReference) {
		this(id, owner, diary, placeId, placeName, category, dateDisplay, memo, address, null, null,
			rating, menu, price, note, photoReference);
	}

	public WishlistEntity(String id, AccountEntity owner, DiaryEntity diary, String placeId, String placeName,
		String category, String dateDisplay, String memo, String address, BigDecimal latitude,
		BigDecimal longitude, BigDecimal rating, String menu, Long price, String note, String photoReference) {
		super(id, owner, diary, placeId, placeName, category, dateDisplay, memo, address,
			latitude, longitude, rating, menu, price, note, photoReference);
	}

	public void update(String placeName, String memo) {
		updateEditableFields(placeName, memo, getRating(), getMenu(), getPrice());
	}
}
