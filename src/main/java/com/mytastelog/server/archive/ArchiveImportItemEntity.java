package com.mytastelog.server.archive;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "archive_import_items", uniqueConstraints = @UniqueConstraint(name = "uk_archive_import_source",
	columnNames = { "account_id", "transfer_id", "source_entity", "source_local_id" }))
public class ArchiveImportItemEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "account_id", nullable = false, length = 36)
	private String accountId;
	@Column(name = "transfer_id", nullable = false, length = 128)
	private String transferId;
	@Column(name = "source_entity", nullable = false, length = 32)
	private String sourceEntity;
	@Column(name = "source_local_id", nullable = false, length = 512)
	private String sourceLocalId;
	@Column(name = "server_id", length = 128)
	private String serverId;
	@Column(nullable = false, length = 32)
	private String status;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ArchiveImportItemEntity() {}

	public ArchiveImportItemEntity(String accountId, String transferId, String sourceEntity,
		String sourceLocalId, String serverId, String status) {
		this.accountId = accountId;
		this.transferId = transferId;
		this.sourceEntity = sourceEntity;
		this.sourceLocalId = sourceLocalId;
		this.serverId = serverId;
		this.status = status;
		this.createdAt = Instant.now();
	}

	public String getServerId() { return serverId; }
	public String getStatus() { return status; }
	public String getTransferId() { return transferId; }
}
