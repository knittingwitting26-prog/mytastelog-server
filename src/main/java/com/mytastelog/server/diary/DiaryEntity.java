package com.mytastelog.server.diary;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.common.persistence.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "diaries", indexes = @Index(name = "idx_diaries_owner", columnList = "owner_id"))
public class DiaryEntity extends BaseTimeEntity {
	@Id
	@Column(length = 128, updatable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private AccountEntity owner;

	@Column(nullable = false, length = 200)
	private String name;

	@Convert(converter = DiaryThemeConverter.class)
	@Column(nullable = false, length = 16)
	private DiaryTheme theme;

	protected DiaryEntity() {
	}

	public DiaryEntity(String id, AccountEntity owner, String name, DiaryTheme theme) {
		this.id = id;
		this.owner = owner;
		this.name = name;
		this.theme = theme;
	}

	public void update(String name, DiaryTheme theme) {
		this.name = name;
		this.theme = theme;
	}

	public String getId() { return id; }
	public AccountEntity getOwner() { return owner; }
	public String getName() { return name; }
	public DiaryTheme getTheme() { return theme; }
}
