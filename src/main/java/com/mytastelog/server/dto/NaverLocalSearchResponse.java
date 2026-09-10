package com.mytastelog.server.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverLocalSearchResponse(List<NaverLocalItem> items) {
}
