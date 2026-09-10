package com.mytastelog.server.exception;

public class NaverAuthenticationException extends RuntimeException {
	private final Integer httpStatus;
	private final String failureCategory;

	public NaverAuthenticationException() {
		this("네이버 API 인증에 실패했습니다.", null, "authentication");
	}

	public NaverAuthenticationException(String message) {
		this(message, null, "authentication");
	}

	public NaverAuthenticationException(String message, Integer httpStatus, String failureCategory) {
		super(message);
		this.httpStatus = httpStatus;
		this.failureCategory = failureCategory;
	}

	public Integer httpStatus() {
		return httpStatus;
	}

	public String failureCategory() {
		return failureCategory;
	}
}
