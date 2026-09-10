package com.mytastelog.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mytastelog.server.client.NaverReverseGeocodeClient;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse.Area;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse.Region;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse.Result;
import com.mytastelog.server.exception.NaverApiException;

class ReverseGeocodeServiceTest {

	private NaverReverseGeocodeClient client;
	private ReverseGeocodeService service;

	@BeforeEach
	void setUp() {
		client = Mockito.mock(NaverReverseGeocodeClient.class);
		service = new ReverseGeocodeService(client);
	}

	@Test
	void prefersAdministrativeDistrictAndRemovesDuplicateEmptyParts() {
		when(client.reverseGeocode(any(), any())).thenReturn(responseWith(
			result("legalcode", "인천광역시", "서구", "경서동", ""),
			result("admcode", "인천광역시", "서구", "청라동", "")
		));

		var response = service.reverseGeocode("37.533", "126.642");

		assertThat(response.area3()).isEqualTo("청라동");
		assertThat(response.displayName()).isEqualTo("인천 서구 청라동");
	}

	@Test
	void fallsBackToLegalDistrictWhenAdministrativeDistrictIsAbsent() {
		when(client.reverseGeocode(any(), any())).thenReturn(responseWith(
			result("legalcode", "경기도", "성남시 분당구", "정자동", "")
		));

		assertThat(service.reverseGeocode("37.36", "127.105").displayName())
			.isEqualTo("경기도 성남시 분당구 정자동");
	}

	@Test
	void rejectsResponseWithoutAddressResults() {
		when(client.reverseGeocode(any(), any())).thenReturn(responseWith());

		assertThatThrownBy(() -> service.reverseGeocode("37.533", "126.642"))
			.isInstanceOf(NaverApiException.class)
			.hasMessage("좌표에 해당하는 주소 결과가 없습니다.");
	}

	private Result result(String name, String area1, String area2, String area3, String area4) {
		return new Result(name, new Region(new Area(area1), new Area(area2), new Area(area3), new Area(area4)));
	}

	private NaverReverseGeocodeResponse responseWith(Result... results) {
		return new NaverReverseGeocodeResponse(
			new NaverReverseGeocodeResponse.Status(0, "ok", "done"),
			List.of(results)
		);
	}
}
