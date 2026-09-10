package com.mytastelog.server.record;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RecordVisibility {
	PRIVATE("private"),
	PUBLIC("public");

	private final String value;

	RecordVisibility(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static RecordVisibility fromValue(String value) {
		for (RecordVisibility visibility : values()) {
			if (visibility.value.equals(value)) return visibility;
		}
		throw new IllegalArgumentException("Unknown record visibility");
	}
}
