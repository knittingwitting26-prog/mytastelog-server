package com.mytastelog.server.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountIdentityEntity;
import com.mytastelog.server.account.AccountIdentityRepository;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.account.AuthenticatedAccount;
import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;

@Service
public class AuthenticationAccountService {
	private final AccountRepository accounts;
	private final AccountIdentityRepository identities;
	private final TransactionTemplate createTransaction;

	public AuthenticationAccountService(AccountRepository accounts, AccountIdentityRepository identities,
		PlatformTransactionManager transactionManager) {
		this.accounts = accounts;
		this.identities = identities;
		this.createTransaction = new TransactionTemplate(transactionManager);
		this.createTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public AuthenticatedAccount resolve(OAuthIdentity identity) {
		return identities.findByProviderAndProviderSubject(identity.provider(), identity.providerSubject())
			.map(existing -> new AuthenticatedAccount(existing.getAccount().getId()))
			.orElseGet(() -> createOrRecover(identity));
	}

	private AuthenticatedAccount createOrRecover(OAuthIdentity identity) {
		try {
			return createTransaction.execute(status -> identities
				.findByProviderAndProviderSubject(identity.provider(), identity.providerSubject())
				.map(existing -> new AuthenticatedAccount(existing.getAccount().getId()))
				.orElseGet(() -> {
					AccountEntity account = accounts.save(AccountEntity.create());
					identities.saveAndFlush(AccountIdentityEntity.create(
						account, identity.provider(), identity.providerSubject()));
					return new AuthenticatedAccount(account.getId());
				}));
		} catch (DataIntegrityViolationException conflict) {
			return identities.findByProviderAndProviderSubject(identity.provider(), identity.providerSubject())
				.map(existing -> new AuthenticatedAccount(existing.getAccount().getId()))
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
					ApiErrorCode.INTERNAL_ERROR, "계정 인증을 완료하지 못했습니다."));
		}
	}
}
