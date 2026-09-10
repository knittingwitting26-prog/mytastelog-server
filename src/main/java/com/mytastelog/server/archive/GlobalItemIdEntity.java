package com.mytastelog.server.archive;

import java.time.Instant;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.collection.ArchiveItemType;
import com.mytastelog.server.collection.ArchiveItemTypeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "archive_item_ids")
public class GlobalItemIdEntity {
	@Id
	@Column(length = 128, updatable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private AccountEntity owner;

	@Convert(converter = ArchiveItemTypeConverter.class)
	@Column(name = "item_type", nullable = false, length = 16, updatable = false)
	private ArchiveItemType itemType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected GlobalItemIdEntity() {
	}

	public GlobalItemIdEntity(String id, AccountEntity owner, ArchiveItemType itemType) {
		this.id = id;
		this.owner = owner;
		this.itemType = itemType;
	}

	@PrePersist
	void initializeCreatedAt() { createdAt = Instant.now(); }
}
