package com.mytastelog.server.exception;

public class NaverApiException extends RuntimeException {
	private final Integer httpStatus;
	private final String failureCategory;

	public NaverApiException(String message) {
		this(message, null, "provider", null);
	}

	public NaverApiException(String message, Throwable cause) {
		this(message, null, "provider", cause);
	}

	public NaverApiException(String message, Integer httpStatus, String failureCategory) {
		this(message, httpStatus, failureCategory, null);
	}

	public NaverApiException(String message, Integer httpStatus, String failureCategory, Throwable cause) {
		super(message, cause);
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
