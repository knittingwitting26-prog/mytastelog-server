package com.mytastelog.server.account;

import java.util.Arrays;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AuthProviderConverter implements AttributeConverter<AuthProvider, String> {
	@Override
	public String convertToDatabaseColumn(AuthProvider attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public AuthProvider convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		return Arrays.stream(AuthProvider.values())
			.filter(value -> value.value().equals(dbData))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown auth provider"));
	}
}
