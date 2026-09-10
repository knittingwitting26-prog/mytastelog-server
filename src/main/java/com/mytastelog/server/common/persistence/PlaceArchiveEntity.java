package com.mytastelog.server.common.persistence;

import java.math.BigDecimal;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.diary.DiaryEntity;
import com.mytastelog.server.photo.PhotoReferenceOwner;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class PlaceArchiveEntity extends BaseTimeEntity implements PhotoReferenceOwner {
	@Id
	@Column(length = 128, updatable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private AccountEntity owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "diary_id", nullable = false, updatable = false)
	private DiaryEntity diary;

	@Column(name = "place_id", nullable = false, length = 255)
	private String placeId;

	@Column(name = "place_name", nullable = false, length = 300)
	private String placeName;

	@Column(nullable = false, length = 100)
	private String category;

	@Column(name = "date_display", nullable = false, length = 200)
	private String dateDisplay;

	@Column(nullable = false, columnDefinition = "text")
	private String memo;

	@Column(nullable = false, length = 500)
	private String address;

	@Column(precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(precision = 2, scale = 1)
	private BigDecimal rating;

	@Column(length = 300)
	private String menu;

	private Long price;

	@Column(columnDefinition = "text")
	private String note;

	@Column(name = "photo_reference", length = 512)
	private String photoReference;

	protected PlaceArchiveEntity() {
	}

	protected PlaceArchiveEntity(String id, AccountEntity owner, DiaryEntity diary, String placeId,
		String placeName, String category, String dateDisplay, String memo, String address,
		BigDecimal rating, String menu, Long price, String note, String photoReference) {
		this(id, owner, diary, placeId, placeName, category, dateDisplay, memo, address,
			null, null, rating, menu, price, note, photoReference);
	}

	protected PlaceArchiveEntity(String id, AccountEntity owner, DiaryEntity diary, String placeId,
		String placeName, String category, String dateDisplay, String memo, String address,
		BigDecimal latitude, BigDecimal longitude, BigDecimal rating, String menu, Long price,
		String note, String photoReference) {
		this.id = id;
		this.owner = owner;
		this.diary = diary;
		this.placeId = placeId;
		this.placeName = placeName;
		this.category = category;
		this.dateDisplay = dateDisplay;
		this.memo = memo;
		this.address = address;
		this.latitude = latitude;
		this.longitude = longitude;
		this.rating = rating;
		this.menu = menu;
		this.price = price;
		this.note = note;
		this.photoReference = photoReference;
	}

	protected void updateEditableFields(String placeName, String memo, BigDecimal rating, String menu, Long price) {
		this.placeName = placeName;
		this.memo = memo;
		this.rating = rating;
		this.menu = menu;
		this.price = price;
	}

	public String getId() { return id; }
	public AccountEntity getOwner() { return owner; }
	public DiaryEntity getDiary() { return diary; }
	public String getPlaceId() { return placeId; }
	public String getPlaceName() { return placeName; }
	public String getCategory() { return category; }
	public String getDateDisplay() { return dateDisplay; }
	public String getMemo() { return memo; }
	public String getAddress() { return address; }
	public BigDecimal getLatitude() { return latitude; }
	public BigDecimal getLongitude() { return longitude; }
	public BigDecimal getRating() { return rating; }
	public String getMenu() { return menu; }
	public Long getPrice() { return price; }
	public String getNote() { return note; }
	public String getPhotoReference() { return photoReference; }
	public void setPhotoReference(String photoReference) { this.photoReference = photoReference; }
}
