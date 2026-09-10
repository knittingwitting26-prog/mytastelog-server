package com.mytastelog.server.photo;

public record PhotoContent(byte[] bytes, String contentType) {
	public PhotoContent {
		bytes = bytes.clone();
	}

	@Override
	public byte[] bytes() {
		return bytes.clone();
	}
}
