package com.mytastelog.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverLocalItem(
	String title,
	String category,
	String address,
	String roadAddress,
	String mapx,
	String mapy
) {
}
