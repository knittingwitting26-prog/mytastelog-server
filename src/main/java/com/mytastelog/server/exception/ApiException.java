package com.mytastelog.server.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final ApiErrorCode code;
	private final String field;

	public ApiException(HttpStatus status, ApiErrorCode code, String message) {
		this(status, code, message, null);
	}

	public ApiException(HttpStatus status, ApiErrorCode code, String message, String field) {
		super(message);
		this.status = status;
		this.code = code;
		this.field = field;
	}

	public HttpStatus status() { return status; }
	public ApiErrorCode code() { return code; }
	public String field() { return field; }
}
