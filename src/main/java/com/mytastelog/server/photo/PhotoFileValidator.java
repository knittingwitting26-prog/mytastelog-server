package com.mytastelog.server.photo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;

@Component
public class PhotoFileValidator {
	public static final long MAX_BYTES = 10L * 1024 * 1024;
	private static final Map<String, byte[]> SIGNATURES = Map.of(
		"image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff},
		"image/png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

	public PhotoContent validate(MultipartFile file) {
		if (file == null || file.isEmpty()) throw validation("빈 사진 파일은 업로드할 수 없습니다.");
		if (file.getSize() > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
			ApiErrorCode.VALIDATION_ERROR, "사진은 10MB 이하만 업로드할 수 있습니다.", "file");
		String contentType = file.getContentType();
		if (!SIGNATURES.containsKey(contentType) && !"image/webp".equals(contentType)) {
			throw validation("JPEG, PNG, WEBP 사진만 업로드할 수 있습니다.");
		}
		try {
			byte[] bytes = file.getBytes();
			if (!hasExpectedSignature(contentType, bytes)) {
				throw validation("파일 내용이 선언된 이미지 형식과 일치하지 않습니다.");
			}
			return new PhotoContent(bytes, contentType);
		} catch (IOException exception) {
			throw validation("사진 파일을 읽을 수 없습니다.");
		}
	}

	private boolean hasExpectedSignature(String contentType, byte[] bytes) {
		if ("image/webp".equals(contentType)) {
			return bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
		}
		byte[] signature = SIGNATURES.get(contentType);
		return bytes.length >= signature.length
			&& Arrays.equals(signature, Arrays.copyOf(bytes, signature.length));
	}

	private boolean ascii(byte[] bytes, int offset, String expected) {
		for (int i = 0; i < expected.length(); i++) if (bytes[offset + i] != expected.charAt(i)) return false;
		return true;
	}

	private ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, message, "file");
	}
}
