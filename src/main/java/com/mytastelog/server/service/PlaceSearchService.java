package com.mytastelog.server.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.mytastelog.server.client.NaverApiHubLocalSearchClient;
import com.mytastelog.server.dto.NaverLocalItem;
import com.mytastelog.server.dto.PlaceSearchResponse;
import com.mytastelog.server.dto.PlaceSearchResponse.Place;
import com.mytastelog.server.exception.InvalidRequestException;
import com.mytastelog.server.exception.NaverApiException;

@Service
public class PlaceSearchService {

	private static final int MAX_QUERY_LENGTH = 100;
	private static final int NAVER_COORDINATE_SCALE = 7;
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private final NaverApiHubLocalSearchClient naverClient;

	public PlaceSearchService(NaverApiHubLocalSearchClient naverClient) {
		this.naverClient = naverClient;
	}

	public PlaceSearchResponse search(String query) {
		String normalizedQuery = validateAndNormalize(query);

		var places = naverClient.search(normalizedQuery).items().stream()
			.map(this::toPlace)
			.toList();
		return new PlaceSearchResponse(places);
	}

	private String validateAndNormalize(String query) {
		if (query == null || query.isBlank()) {
			throw new InvalidRequestException("검색어를 입력해 주세요.");
		}
		if (query.length() > MAX_QUERY_LENGTH) {
			throw new InvalidRequestException("검색어는 100자 이하로 입력해 주세요.");
		}
		return query.trim();
	}

	private Place toPlace(NaverLocalItem item) {
		String name = normalizeText(item.title());
		String address = normalizeText(item.address());
		String roadAddress = normalizeText(item.roadAddress());
		if (name.isBlank() || (address.isBlank() && roadAddress.isBlank())) throw new NaverApiException(
			"NAVER 장소 검색 응답에 필수 정보가 없습니다.", null, "response_mapping");
		BigDecimal longitude = toCoordinate(item.mapx(), new BigDecimal("-180"), new BigDecimal("180"));
		BigDecimal latitude = toCoordinate(item.mapy(), new BigDecimal("-90"), new BigDecimal("90"));
		return new Place(
			placeId(name, roadAddress.isBlank() ? address : roadAddress, longitude, latitude),
			"NAVER", null, name, normalizeText(item.category()), address, roadAddress, longitude, latitude
		);
	}

	private String normalizeText(String value) {
		if (value == null) return "";
		String plain = HtmlUtils.htmlUnescape(HTML_TAG.matcher(value).replaceAll(""));
		return WHITESPACE.matcher(Normalizer.normalize(plain, Normalizer.Form.NFKC).trim()).replaceAll(" ");
	}

	private String placeId(String name, String address, BigDecimal longitude, BigDecimal latitude) {
		String key = name + "|" + address + "|" + coordinateKey(longitude) + "|" + coordinateKey(latitude);
		try {
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
			return "naver:" + hash.substring(0, 32);
		} catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
	}

	private String coordinateKey(BigDecimal value) { return value.setScale(7, RoundingMode.HALF_UP).toPlainString(); }

	private BigDecimal toCoordinate(String coordinate, BigDecimal minimum, BigDecimal maximum) {
		try {
			BigDecimal value = new BigDecimal(coordinate).movePointLeft(NAVER_COORDINATE_SCALE);
			if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
				throw new NumberFormatException("coordinate out of WGS84 range");
			}
			return value;
		} catch (NumberFormatException | NullPointerException exception) {
			throw new NaverApiException("NAVER API HUB 장소 검색 좌표 형식이 올바르지 않습니다.",
				null, "response_mapping", exception);
		}
	}
}
