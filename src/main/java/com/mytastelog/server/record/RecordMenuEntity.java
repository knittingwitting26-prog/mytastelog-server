package com.mytastelog.server.record;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.common.persistence.BaseTimeEntity;
import com.mytastelog.server.photo.PhotoReferenceOwner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "record_menus",
	indexes = @Index(name = "idx_record_menus_record_position", columnList = "record_id, position"),
	uniqueConstraints = @UniqueConstraint(name = "uk_record_menus_record_position", columnNames = {"record_id", "position"}))
public class RecordMenuEntity extends BaseTimeEntity implements PhotoReferenceOwner {
	@Id
	@Column(length = 128, updatable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "record_id", nullable = false, updatable = false)
	private RecordEntity record;

	@Column(length = 300)
	private String name;

	private Long price;

	@Column(nullable = false)
	private int position;

	@Column(name = "photo_reference", length = 512)
	private String photoReference;

	protected RecordMenuEntity() {
	}

	public RecordMenuEntity(String id, RecordEntity record, String name, Long price, int position) {
		this(id, record, name, price, position, null);
	}

	public RecordMenuEntity(String id, RecordEntity record, String name, Long price, int position,
		String photoReference) {
		this.id = id;
		this.record = record;
		this.name = name;
		this.price = price;
		this.position = position;
		this.photoReference = photoReference;
	}

	public String getId() { return id; }
	public RecordEntity getRecord() { return record; }
	@Override public AccountEntity getOwner() { return record.getOwner(); }
	public String getName() { return name; }
	public Long getPrice() { return price; }
	public int getPosition() { return position; }
	public String getPhotoReference() { return photoReference; }
	@Override public void setPhotoReference(String photoReference) { this.photoReference = photoReference; }
}
