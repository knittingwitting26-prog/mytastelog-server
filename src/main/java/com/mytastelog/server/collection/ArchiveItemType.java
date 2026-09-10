package com.mytastelog.server.collection;

public enum ArchiveItemType {
	RECORD("record"),
	WISHLIST("wishlist");

	private final String value;

	ArchiveItemType(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}
}
