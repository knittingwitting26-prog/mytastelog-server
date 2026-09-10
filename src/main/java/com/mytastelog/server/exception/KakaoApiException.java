package com.mytastelog.server.exception;

public class KakaoApiException extends RuntimeException {
	public KakaoApiException(String message) { super(message); }
	public KakaoApiException(String message, Throwable cause) { super(message, cause); }
}
