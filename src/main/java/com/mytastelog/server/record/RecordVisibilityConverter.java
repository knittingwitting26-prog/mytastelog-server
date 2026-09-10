package com.mytastelog.server.record;

import java.util.Arrays;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RecordVisibilityConverter implements AttributeConverter<RecordVisibility, String> {
	@Override
	public String convertToDatabaseColumn(RecordVisibility attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public RecordVisibility convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		return Arrays.stream(RecordVisibility.values())
			.filter(value -> value.value().equals(dbData))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown record visibility"));
	}
}
