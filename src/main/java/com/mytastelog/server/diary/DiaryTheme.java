package com.mytastelog.server.diary;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DiaryTheme {
	NOTEBOOK("notebook"),
	TRAVEL("travel");

	private final String value;

	DiaryTheme(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static DiaryTheme fromValue(String value) {
		for (DiaryTheme theme : values()) {
			if (theme.value.equals(value)) return theme;
		}
		throw new IllegalArgumentException("Unknown diary theme");
	}
}
