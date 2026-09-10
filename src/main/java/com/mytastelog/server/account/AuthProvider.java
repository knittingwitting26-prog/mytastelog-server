package com.mytastelog.server.account;

public enum AuthProvider {
	GOOGLE("google"),
	KAKAO("kakao"),
	NAVER("naver");

	private final String value;

	AuthProvider(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}
}
