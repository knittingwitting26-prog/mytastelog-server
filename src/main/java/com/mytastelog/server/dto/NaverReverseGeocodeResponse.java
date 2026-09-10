package com.mytastelog.server.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverReverseGeocodeResponse(
	Status status,
	List<Result> results
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Status(int code, String name, String message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Result(String name, Region region) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Region(Area area1, Area area2, Area area3, Area area4) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Area(String name) {
	}
}
