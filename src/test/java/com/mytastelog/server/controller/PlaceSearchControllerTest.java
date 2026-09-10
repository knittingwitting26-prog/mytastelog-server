package com.mytastelog.server.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mytastelog.server.dto.PlaceSearchResponse;
import com.mytastelog.server.dto.PlaceSearchResponse.Place;
import com.mytastelog.server.exception.GlobalExceptionHandler;
import com.mytastelog.server.exception.InvalidRequestException;
import com.mytastelog.server.exception.KakaoApiException;
import com.mytastelog.server.service.PlaceSearchService;

class PlaceSearchControllerTest {

	private PlaceSearchService placeSearchService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		placeSearchService = org.mockito.Mockito.mock(PlaceSearchService.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new PlaceSearchController(placeSearchService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void validQueryReturnsPlacesAsInternalResponse() throws Exception {
		var place = new Place(
			"naver:0123456789abcdef0123456789abcdef",
			"NAVER",
			null,
			"스타벅스 청라점",
			"카페,디저트",
			"인천광역시 서구 청라동",
			"인천광역시 서구 청라로",
			new BigDecimal("126.6420000"),
			new BigDecimal("37.5330000")
		);
		when(placeSearchService.search("스타벅스 청라"))
			.thenReturn(new PlaceSearchResponse(List.of(place)));

		mockMvc.perform(get("/api/v1/places/search").param("query", "스타벅스 청라"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.places[0].source").value("NAVER"))
			.andExpect(jsonPath("$.data.places[0].placeId").value("naver:0123456789abcdef0123456789abcdef"))
			.andExpect(jsonPath("$.data.places[0].providerPlaceId").doesNotExist())
			.andExpect(jsonPath("$.data.places[0].name").value("스타벅스 청라점"))
			.andExpect(jsonPath("$.data.places[0].longitude").value(126.642))
			.andExpect(jsonPath("$.data.places[0].latitude").value(37.533));
	}

	@Test
	void missingQueryReturnsBadRequest() throws Exception {
		when(placeSearchService.search(null))
			.thenThrow(new InvalidRequestException("검색어를 입력해 주세요."));

		mockMvc.perform(get("/api/v1/places/search"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.error.message").value("검색어를 입력해 주세요."));
	}

	@Test
	void blankQueryReturnsBadRequest() throws Exception {
		when(placeSearchService.search("   "))
			.thenThrow(new InvalidRequestException("검색어를 입력해 주세요."));

		mockMvc.perform(get("/api/v1/places/search").param("query", "   "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void kakaoApiFailureReturnsBadGateway() throws Exception {
		when(placeSearchService.search("스타벅스"))
			.thenThrow(new KakaoApiException("카카오 장소 검색 API 호출에 실패했습니다."));

		mockMvc.perform(get("/api/v1/places/search").param("query", "스타벅스"))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
	}

	@Test
	void unexpectedFailureReturnsInternalServerError() throws Exception {
		when(placeSearchService.search("스타벅스"))
			.thenThrow(new RuntimeException("unexpected"));

		mockMvc.perform(get("/api/v1/places/search").param("query", "스타벅스"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."));
	}
}
