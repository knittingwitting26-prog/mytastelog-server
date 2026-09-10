package com.mytastelog.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mytastelog.server.client.NaverApiHubLocalSearchClient;
import com.mytastelog.server.dto.NaverLocalItem;
import com.mytastelog.server.dto.NaverLocalSearchResponse;
import com.mytastelog.server.exception.InvalidRequestException;
import com.mytastelog.server.exception.NaverApiException;

class PlaceSearchServiceTest {
	private NaverApiHubLocalSearchClient client;
	private PlaceSearchService service;

	@BeforeEach void setUp() {
		client = Mockito.mock(NaverApiHubLocalSearchClient.class);
		service = new PlaceSearchService(client);
	}

	@Test void mapsProviderNeutralNaverResultAndTrimsQuery() {
		when(client.search("스타벅스 청라")).thenReturn(response("<b>스타벅스</b>  청라점", "1266420000", "375330000"));
		var place = service.search("  스타벅스 청라  ").places().getFirst();
		assertThat(place.source()).isEqualTo("NAVER");
		assertThat(place.providerPlaceId()).isNull();
		assertThat(place.name()).isEqualTo("스타벅스 청라점");
		assertThat(place.longitude()).isEqualByComparingTo("126.6420000");
		assertThat(place.latitude()).isEqualByComparingTo("37.5330000");
		assertThat(place.placeId()).startsWith("naver:").hasSize(38);
	}

	@Test void sameResultProducesSameIdentityAndDifferentResultDoesNot() {
		when(client.search("첫 검색")).thenReturn(response("카페", "1266420001", "375330001"));
		when(client.search("재검색")).thenReturn(response("카페", "1266420001", "375330001"));
		when(client.search("다른 곳")).thenReturn(response("다른 카페", "1266420000", "375330000"));
		String first = service.search("첫 검색").places().getFirst().placeId();
		assertThat(service.search("재검색").places().getFirst().placeId()).isEqualTo(first);
		assertThat(service.search("다른 곳").places().getFirst().placeId()).isNotEqualTo(first);
	}

	@Test void acceptsEmptyResults() {
		when(client.search("없는 장소")).thenReturn(new NaverLocalSearchResponse(List.of()));
		assertThat(service.search("없는 장소").places()).isEmpty();
	}

	@Test void rejectsInvalidCoordinate() {
		when(client.search("좌표 테스트")).thenReturn(response("장소", "invalid", "37"));
		assertThatThrownBy(() -> service.search("좌표 테스트")).isInstanceOf(NaverApiException.class);
	}

	@Test void validatesQuery() {
		assertThatThrownBy(() -> service.search("   ")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.search("가".repeat(101))).isInstanceOf(InvalidRequestException.class);
	}

	private NaverLocalSearchResponse response(String name, String mapx, String mapy) {
		return new NaverLocalSearchResponse(List.of(new NaverLocalItem(name, "카페,디저트", "지번 주소", "도로명 주소", mapx, mapy)));
	}
}
