package com.mytastelog.server.diary;

import java.util.Arrays;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DiaryThemeConverter implements AttributeConverter<DiaryTheme, String> {
	@Override
	public String convertToDatabaseColumn(DiaryTheme attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public DiaryTheme convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		return Arrays.stream(DiaryTheme.values())
			.filter(value -> value.value().equals(dbData))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown diary theme"));
	}
}
