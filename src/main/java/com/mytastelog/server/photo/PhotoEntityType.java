package com.mytastelog.server.photo;

public enum PhotoEntityType {
	RECORD("records"),
	WISHLIST("wishlist"),
	COLLECTION("collections");

	private final String path;

	PhotoEntityType(String path) {
		this.path = path;
	}

	public String path() {
		return path;
	}
}
