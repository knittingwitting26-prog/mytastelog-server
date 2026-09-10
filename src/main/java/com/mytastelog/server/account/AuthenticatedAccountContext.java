package com.mytastelog.server.account;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;

@Component
public class AuthenticatedAccountContext {
	public String requireAccountId(Authentication authentication) {
		if (authentication != null && authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof AuthenticatedAccount account
			&& account.accountId() != null && !account.accountId().isBlank()) {
			return account.accountId();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
	}
}
