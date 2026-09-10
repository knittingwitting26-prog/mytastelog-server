package com.mytastelog.server.auth;

import org.springframework.security.core.AuthenticationException;

public class AccountResolutionAuthenticationException extends AuthenticationException {
	public AccountResolutionAuthenticationException() {
		super("Account resolution failed");
	}
}
