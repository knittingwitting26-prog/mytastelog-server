package com.mytastelog.server.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.mytastelog.server.exception.ApiException;

class PhotoFileValidatorTest {
	private final PhotoFileValidator validator = new PhotoFileValidator();

	@Test
	void acceptsJpegPngAndWebpUsingDeclaredMimeAndMagicBytes() {
		assertThat(validator.validate(file("image/jpeg", bytes(0xff, 0xd8, 0xff, 0x00))).contentType())
			.isEqualTo("image/jpeg");
		assertThat(validator.validate(file("image/png", bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
			.contentType()).isEqualTo("image/png");
		assertThat(validator.validate(file("image/webp", "RIFF1234WEBP".getBytes())).contentType())
			.isEqualTo("image/webp");
	}

	@Test
	void rejectsEmptyUnsupportedMismatchedAndOversizedFiles() {
		assertInvalid(new MockMultipartFile("file", new byte[0]));
		assertInvalid(file("image/gif", "GIF89a".getBytes()));
		assertInvalid(file("image/jpeg", "plain text".getBytes()));
		assertInvalid(file("image/png", bytes(0xff, 0xd8, 0xff)));
		assertInvalid(file("image/jpeg", new byte[(int) PhotoFileValidator.MAX_BYTES + 1]));
	}

	private MockMultipartFile file(String contentType, byte[] bytes) {
		return new MockMultipartFile("file", "ignored.exe", contentType, bytes);
	}

	private byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
		return result;
	}

	private void assertInvalid(MockMultipartFile file) {
		assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ApiException.class);
	}
}
