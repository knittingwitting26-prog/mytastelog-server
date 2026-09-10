package com.mytastelog.server.auth;

public final class AuthResponses {
	private AuthResponses() {}

	public record ApiSuccess<T>(T data) {}
	public record CurrentAccountResponse(String id) {}
	public record CsrfResponse(String headerName, String parameterName, String token) {}
}
