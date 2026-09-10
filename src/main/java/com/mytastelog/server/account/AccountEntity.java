package com.mytastelog.server.account;

import java.util.UUID;

import com.mytastelog.server.common.persistence.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class AccountEntity extends BaseTimeEntity {
	@Id
	@Column(length = 36, updatable = false)
	private String id;

	protected AccountEntity() {
	}

	public static AccountEntity create() {
		return new AccountEntity();
	}

	@PrePersist
	void initializeId() {
		if (id == null) id = UUID.randomUUID().toString();
	}

	public String getId() { return id; }
}
