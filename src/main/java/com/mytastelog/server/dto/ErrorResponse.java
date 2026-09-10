package com.mytastelog.server.dto;

public record ErrorResponse(ErrorDetail error) {

	public record ErrorDetail(String code, String message, String field) {
	}

	public static ErrorResponse of(String code, String message) {
		return of(code, message, null);
	}

	public static ErrorResponse of(String code, String message, String field) {
		return new ErrorResponse(new ErrorDetail(code, message, field));
	}
}
