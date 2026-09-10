package com.mytastelog.server.revisit;

import java.time.Instant;
import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.diary.DiaryEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "revisit_intents", uniqueConstraints = @UniqueConstraint(name = "uk_revisit_owner_diary_place", columnNames = { "owner_id", "diary_id", "place_id" }))
public class RevisitIntentEntity {
	@Id @Column(length = 128, updatable = false) private String id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false, updatable = false) private AccountEntity owner;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "diary_id", nullable = false, updatable = false) private DiaryEntity diary;
	@Column(name = "place_id", nullable = false, length = 255, updatable = false) private String placeId;
	@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
	protected RevisitIntentEntity() {}
	public RevisitIntentEntity(String id, AccountEntity owner, DiaryEntity diary, String placeId) { this.id = id; this.owner = owner; this.diary = diary; this.placeId = placeId; }
	@PrePersist void initializeCreatedAt() { createdAt = Instant.now(); }
	public String getId() { return id; }
	public AccountEntity getOwner() { return owner; }
	public DiaryEntity getDiary() { return diary; }
	public String getPlaceId() { return placeId; }
	public Instant getCreatedAt() { return createdAt; }
}
