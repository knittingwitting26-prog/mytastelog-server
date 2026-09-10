package com.mytastelog.server.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mytastelog.server.account.AuthenticatedAccountContext;
import com.mytastelog.server.auth.AuthResponses.ApiSuccess;
import com.mytastelog.server.auth.AuthResponses.CsrfResponse;
import com.mytastelog.server.auth.AuthResponses.CurrentAccountResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
	private final AuthenticatedAccountContext accountContext;

	public AuthController(AuthenticatedAccountContext accountContext) {
		this.accountContext = accountContext;
	}

	@GetMapping("/me")
	public ResponseEntity<ApiSuccess<CurrentAccountResponse>> me(Authentication authentication) {
		return ResponseEntity.ok(new ApiSuccess<>(
			new CurrentAccountResponse(accountContext.requireAccountId(authentication))));
	}

	@GetMapping("/csrf")
	public ResponseEntity<ApiSuccess<CsrfResponse>> csrf(CsrfToken token) {
		return ResponseEntity.ok(new ApiSuccess<>(
			new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken())));
	}
}
