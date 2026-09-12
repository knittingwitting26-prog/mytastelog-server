package com.mytastelog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mytastelog.server.account.AccountIdentityRepository;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.account.AuthProvider;
import com.mytastelog.server.account.AuthenticatedAccount;
import com.mytastelog.server.exception.ApiException;

@SpringBootTest
@ActiveProfiles("test")
class AuthenticationAccountServiceIntegrationTest {
	@Autowired AuthenticationAccountService service;
	@Autowired AccountRepository accounts;
	@Autowired AccountIdentityRepository identities;

	@BeforeEach
	void cleanDatabase() {
		identities.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void firstLoginCreatesAtomicAccountAndIdentityAndReloginReusesIt() {
		AuthenticatedAccount first = service.resolve(new OAuthIdentity(AuthProvider.GOOGLE, "subject-a"));
		AuthenticatedAccount again = service.resolve(new OAuthIdentity(AuthProvider.GOOGLE, "subject-a"));

		assertThat(again.accountId()).isEqualTo(first.accountId());
		assertThat(accounts.count()).isEqualTo(1);
		assertThat(identities.count()).isEqualTo(1);
		assertThat(identities.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "subject-a"))
			.get().extracting(identity -> identity.getAccount().getId()).isEqualTo(first.accountId());
	}

	@Test
	void kakaoFirstLoginCreatesAtomicIdentityAndReloginReusesIt() {
		AuthenticatedAccount first = service.resolve(new OAuthIdentity(AuthProvider.KAKAO, "kakao-subject-a"));
		AuthenticatedAccount again = service.resolve(new OAuthIdentity(AuthProvider.KAKAO, "kakao-subject-a"));

		assertThat(again.accountId()).isEqualTo(first.accountId());
		assertThat(accounts.count()).isEqualTo(1);
		assertThat(identities.findByProviderAndProviderSubject(AuthProvider.KAKAO, "kakao-subject-a"))
			.get().extracting(identity -> identity.getAccount().getId()).isEqualTo(first.accountId());
	}

	@Test
	void equalSubjectsAcrossGoogleAndKakaoNeverMerge() {
		AuthenticatedAccount google = service.resolve(new OAuthIdentity(AuthProvider.GOOGLE, "same-provider-email"));
		AuthenticatedAccount kakao = service.resolve(new OAuthIdentity(AuthProvider.KAKAO, "same-provider-email"));

		assertThat(kakao.accountId()).isNotEqualTo(google.accountId());
		assertThat(accounts.count()).isEqualTo(2);
		assertThat(identities.count()).isEqualTo(2);
	}

	@Test
	void naverFirstLoginReusesIdentityAndDifferentSubjectCreatesAnotherAccount() {
		AuthenticatedAccount first = service.resolve(new OAuthIdentity(AuthProvider.NAVER, "naver-n1"));
		AuthenticatedAccount again = service.resolve(new OAuthIdentity(AuthProvider.NAVER, "naver-n1"));
		AuthenticatedAccount second = service.resolve(new OAuthIdentity(AuthProvider.NAVER, "naver-n2"));

		assertThat(again.accountId()).isEqualTo(first.accountId());
		assertThat(second.accountId()).isNotEqualTo(first.accountId());
		assertThat(accounts.count()).isEqualTo(2);
		assertThat(identities.count()).isEqualTo(2);
	}

	@Test
	void equalSubjectStringsAcrossAllProvidersNeverMerge() {
		AuthenticatedAccount google = service.resolve(new OAuthIdentity(AuthProvider.GOOGLE, "same-subject"));
		AuthenticatedAccount kakao = service.resolve(new OAuthIdentity(AuthProvider.KAKAO, "same-subject"));
		AuthenticatedAccount naver = service.resolve(new OAuthIdentity(AuthProvider.NAVER, "same-subject"));

		assertThat(List.of(google.accountId(), kakao.accountId(), naver.accountId())).doesNotHaveDuplicates();
		assertThat(accounts.count()).isEqualTo(3);
		assertThat(identities.count()).isEqualTo(3);
	}

	@Test
	void differentSubjectsNeverMergeEvenWhenProviderCouldReturnTheSameEmail() {
		AuthenticatedAccount first = service.resolve(new OAuthIdentity(AuthProvider.GOOGLE, "subject-email-1"));
		AuthenticatedAccount second = service.resolve(new OAuthIdentity(AuthProvider.GOOGLE, "subject-email-2"));

		assertThat(second.accountId()).isNotEqualTo(first.accountId());
		assertThat(accounts.count()).isEqualTo(2);
		assertThat(identities.count()).isEqualTo(2);
	}

	@Test
	void accountAndIdentityRollbackTogetherWhenIdentityCannotBePersisted() {
		String oversizedSubject = "s".repeat(300);
		assertThatThrownBy(() -> service.resolve(new OAuthIdentity(AuthProvider.GOOGLE, oversizedSubject)))
			.isInstanceOf(ApiException.class);
		assertThat(accounts.count()).isZero();
		assertThat(identities.count()).isZero();
	}

	@Test
	void concurrentFirstLoginProducesOneAccountAndOneIdentity() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<AuthenticatedAccount> login = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return service.resolve(new OAuthIdentity(AuthProvider.GOOGLE, "concurrent-subject"));
		};

		try (var executor = Executors.newFixedThreadPool(2)) {
			var results = List.of(executor.submit(login), executor.submit(login));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			String firstId = results.get(0).get(10, TimeUnit.SECONDS).accountId();
			String secondId = results.get(1).get(10, TimeUnit.SECONDS).accountId();
			assertThat(secondId).isEqualTo(firstId);
		}

		assertThat(accounts.count()).isEqualTo(1);
		assertThat(identities.count()).isEqualTo(1);
	}

	@Test
	void concurrentKakaoFirstLoginProducesOneAccountAndOneIdentity() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<AuthenticatedAccount> login = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return service.resolve(new OAuthIdentity(AuthProvider.KAKAO, "concurrent-kakao-subject"));
		};

		try (var executor = Executors.newFixedThreadPool(2)) {
			var results = List.of(executor.submit(login), executor.submit(login));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			String firstId = results.get(0).get(10, TimeUnit.SECONDS).accountId();
			String secondId = results.get(1).get(10, TimeUnit.SECONDS).accountId();
			assertThat(secondId).isEqualTo(firstId);
		}

		assertThat(accounts.count()).isEqualTo(1);
		assertThat(identities.count()).isEqualTo(1);
	}

	@Test
	void concurrentNaverFirstLoginProducesOneAccountAndOneIdentity() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<AuthenticatedAccount> login = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return service.resolve(new OAuthIdentity(AuthProvider.NAVER, "concurrent-naver-subject"));
		};

		try (var executor = Executors.newFixedThreadPool(2)) {
			var results = List.of(executor.submit(login), executor.submit(login));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			String firstId = results.get(0).get(10, TimeUnit.SECONDS).accountId();
			String secondId = results.get(1).get(10, TimeUnit.SECONDS).accountId();
			assertThat(secondId).isEqualTo(firstId);
		}

		assertThat(accounts.count()).isEqualTo(1);
		assertThat(identities.count()).isEqualTo(1);
	}
}
