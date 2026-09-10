package com.mytastelog.server.account;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "account_identities", uniqueConstraints =
	@UniqueConstraint(name = "uk_account_identity_provider_subject", columnNames = {"provider", "provider_subject"}))
public class AccountIdentityEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private AccountEntity account;

	@Convert(converter = AuthProviderConverter.class)
	@Column(nullable = false, length = 16)
	private AuthProvider provider;

	@Column(name = "provider_subject", nullable = false, length = 255)
	private String providerSubject;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AccountIdentityEntity() {
	}

	public static AccountIdentityEntity create(AccountEntity account, AuthProvider provider, String providerSubject) {
		AccountIdentityEntity identity = new AccountIdentityEntity();
		identity.account = account;
		identity.provider = provider;
		identity.providerSubject = providerSubject;
		return identity;
	}

	@PrePersist
	void initializeCreatedAt() {
		createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public AccountEntity getAccount() { return account; }
	public AuthProvider getProvider() { return provider; }
	public String getProviderSubject() { return providerSubject; }
	public Instant getCreatedAt() { return createdAt; }
}
