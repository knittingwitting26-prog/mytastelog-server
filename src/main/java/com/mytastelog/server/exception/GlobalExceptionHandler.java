package com.mytastelog.server.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.mytastelog.server.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApi(ApiException exception) {
		return error(exception.status(), exception.code(), exception.getMessage(), exception.field());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
		var fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst();
		return error(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
			fieldError.map(error -> error.getDefaultMessage()).orElse("요청 값이 올바르지 않습니다."),
			fieldError.map(error -> error.getField()).orElse(null));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
		return error(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
			"요청 JSON 형식 또는 값이 올바르지 않습니다.", null);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
		return error(HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.VALIDATION_ERROR,
			"사진은 10MB 이하만 업로드할 수 있습니다.", "file");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception) {
		log.warn("Persistence constraint rejected an API operation", exception);
		return error(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT,
			"요청이 현재 데이터 상태와 충돌합니다.", null);
	}

	@ExceptionHandler(InvalidRequestException.class)
	ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException exception) {
		return error(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, exception.getMessage(), null);
	}

	@ExceptionHandler(NaverAuthenticationException.class)
	ResponseEntity<ErrorResponse> handleNaverAuthentication(NaverAuthenticationException exception) {
		logProviderFailure(exception.httpStatus(), exception.failureCategory(), exception);
		return error(HttpStatus.BAD_GATEWAY, ApiErrorCode.INTERNAL_ERROR, "외부 장소 서비스 인증에 실패했습니다.", null);
	}

	@ExceptionHandler(NaverApiException.class)
	ResponseEntity<ErrorResponse> handleNaverApi(NaverApiException exception) {
		logProviderFailure(exception.httpStatus(), exception.failureCategory(), exception);
		return error(HttpStatus.BAD_GATEWAY, ApiErrorCode.INTERNAL_ERROR, "외부 장소 서비스를 사용할 수 없습니다.", null);
	}

	private void logProviderFailure(Integer httpStatus, String category, RuntimeException exception) {
		Throwable cause = exception.getCause();
		log.warn("External provider failure provider=NAVER httpStatus={} category={} exceptionType={} causeType={}",
			httpStatus == null ? "none" : httpStatus, category, exception.getClass().getSimpleName(),
			cause == null ? "none" : cause.getClass().getSimpleName());
	}

	@ExceptionHandler(KakaoApiException.class)
	ResponseEntity<ErrorResponse> handleKakaoApi(KakaoApiException exception) {
		return error(HttpStatus.BAD_GATEWAY, ApiErrorCode.INTERNAL_ERROR, "외부 장소 서비스를 사용할 수 없습니다.", null);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
		log.error("Unhandled API exception", exception);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, "서버 내부 오류가 발생했습니다.", null);
	}

	private ResponseEntity<ErrorResponse> error(HttpStatus status, ApiErrorCode code, String message, String field) {
		return ResponseEntity.status(status)
			.body(ErrorResponse.of(code.name(), message, field));
	}
}
