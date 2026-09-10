package com.mytastelog.server.collection;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.common.persistence.BaseTimeEntity;
import com.mytastelog.server.diary.DiaryEntity;
import com.mytastelog.server.photo.PhotoReferenceOwner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "collections", indexes = @Index(name = "idx_collections_owner_diary", columnList = "owner_id, diary_id"))
public class CollectionEntity extends BaseTimeEntity implements PhotoReferenceOwner {
	@Id
	@Column(length = 128, updatable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private AccountEntity owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "diary_id", nullable = false, updatable = false)
	private DiaryEntity diary;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, columnDefinition = "text")
	private String memo;

	@Column(nullable = false)
	private int position;

	@Column(name = "photo_reference", length = 512)
	private String photoReference;

	protected CollectionEntity() {
	}

	public CollectionEntity(String id, AccountEntity owner, DiaryEntity diary, String name, String memo, int position) {
		this.id = id;
		this.owner = owner;
		this.diary = diary;
		this.name = name;
		this.memo = memo;
		this.position = position;
	}

	public void updateName(String name) { this.name = name; }
	public void moveTo(int position) { this.position = position; }

	public String getId() { return id; }
	public AccountEntity getOwner() { return owner; }
	public DiaryEntity getDiary() { return diary; }
	public String getName() { return name; }
	public String getMemo() { return memo; }
	public int getPosition() { return position; }
	public String getPhotoReference() { return photoReference; }
	public void setPhotoReference(String photoReference) { this.photoReference = photoReference; }
}
