package com.mytastelog.server.service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.mytastelog.server.client.NaverReverseGeocodeClient;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse.Result;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse.Region;
import com.mytastelog.server.dto.ReverseGeocodeResponse;
import com.mytastelog.server.exception.InvalidRequestException;
import com.mytastelog.server.exception.NaverApiException;

@Service
public class ReverseGeocodeService {

	private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
	private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
	private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
	private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
	private static final List<String> RESULT_PRIORITY = List.of("admcode", "legalcode", "addr", "roadaddr");
	private static final Map<String, String> AREA1_ABBREVIATIONS = Map.ofEntries(
		Map.entry("서울특별시", "서울"),
		Map.entry("부산광역시", "부산"),
		Map.entry("대구광역시", "대구"),
		Map.entry("인천광역시", "인천"),
		Map.entry("광주광역시", "광주"),
		Map.entry("대전광역시", "대전"),
		Map.entry("울산광역시", "울산"),
		Map.entry("세종특별자치시", "세종"),
		Map.entry("제주특별자치도", "제주")
	);

	private final NaverReverseGeocodeClient client;

	public ReverseGeocodeService(NaverReverseGeocodeClient client) {
		this.client = client;
	}

	public ReverseGeocodeResponse reverseGeocode(String latitudeValue, String longitudeValue) {
		BigDecimal latitude = parseCoordinate(latitudeValue, "위도", MIN_LATITUDE, MAX_LATITUDE);
		BigDecimal longitude = parseCoordinate(longitudeValue, "경도", MIN_LONGITUDE, MAX_LONGITUDE);
		Result result = selectBestResult(client.reverseGeocode(latitude, longitude).results());
		Region region = result.region();
		String area1 = areaName(region == null ? null : region.area1());
		String area2 = areaName(region == null ? null : region.area2());
		String area3 = firstNonBlank(
			areaName(region == null ? null : region.area3()),
			areaName(region == null ? null : region.area4())
		);
		String displayName = buildDisplayName(area1, area2, area3);
		if (displayName.isBlank()) {
			throw new NaverApiException("좌표에 해당하는 지역명을 찾을 수 없습니다.");
		}
		return new ReverseGeocodeResponse(area1, area2, area3, displayName);
	}

	private BigDecimal parseCoordinate(String value, String label, BigDecimal min, BigDecimal max) {
		if (value == null || value.isBlank()) {
			throw new InvalidRequestException(label + "를 입력해 주세요.");
		}
		try {
			BigDecimal coordinate = new BigDecimal(value.trim());
			if (coordinate.compareTo(min) < 0 || coordinate.compareTo(max) > 0) {
				throw new InvalidRequestException(label + " 범위를 확인해 주세요.");
			}
			return coordinate;
		} catch (NumberFormatException exception) {
			throw new InvalidRequestException(label + "는 숫자 형식이어야 합니다.");
		}
	}

	private Result selectBestResult(List<Result> results) {
		if (results == null || results.isEmpty()) {
			throw new NaverApiException("좌표에 해당하는 주소 결과가 없습니다.");
		}
		return RESULT_PRIORITY.stream()
			.flatMap(name -> results.stream().filter(result -> name.equals(result.name())))
			.filter(result -> result.region() != null)
			.findFirst()
			.orElseThrow(() -> new NaverApiException("좌표에 해당하는 지역명을 찾을 수 없습니다."));
	}

	private String areaName(com.mytastelog.server.dto.NaverReverseGeocodeResponse.Area area) {
		return area == null || area.name() == null ? "" : area.name().trim();
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (!value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private String buildDisplayName(String area1, String area2, String area3) {
		Set<String> parts = new LinkedHashSet<>();
		addIfPresent(parts, AREA1_ABBREVIATIONS.getOrDefault(area1, area1));
		addIfPresent(parts, area2);
		addIfPresent(parts, area3);
		return String.join(" ", parts);
	}

	private void addIfPresent(Set<String> parts, String value) {
		if (value != null && !value.isBlank()) {
			parts.add(value.trim());
		}
	}
}
