package com.mytastelog.server.collection;

import java.util.Arrays;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ArchiveItemTypeConverter implements AttributeConverter<ArchiveItemType, String> {
	@Override
	public String convertToDatabaseColumn(ArchiveItemType attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public ArchiveItemType convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		return Arrays.stream(ArchiveItemType.values())
			.filter(value -> value.value().equals(dbData))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown archive item type"));
	}
}
