package com.mytastelog.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mytastelog.server.client.NaverReverseGeocodeClient;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse.Area;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse.Region;
import com.mytastelog.server.dto.NaverReverseGeocodeResponse.Result;
import com.mytastelog.server.exception.GlobalExceptionHandler;
import com.mytastelog.server.service.ReverseGeocodeService;

class ReverseGeocodeControllerTest {

	private NaverReverseGeocodeClient client;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		client = org.mockito.Mockito.mock(NaverReverseGeocodeClient.class);
		var service = new ReverseGeocodeService(client);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new ReverseGeocodeController(service))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void validCoordinatesReturnNormalizedArea() throws Exception {
		when(client.reverseGeocode(any(), any())).thenReturn(responseWith(
			new Result("admcode", new Region(
				new Area("인천광역시"), new Area("서구"), new Area("청라동"), new Area("")
			))
		));

		mockMvc.perform(get("/api/v1/places/reverse-geocode")
				.param("latitude", "37.533")
				.param("longitude", "126.642"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.area1").value("인천광역시"))
			.andExpect(jsonPath("$.area2").value("서구"))
			.andExpect(jsonPath("$.area3").value("청라동"))
			.andExpect(jsonPath("$.displayName").value("인천 서구 청라동"));
	}

	@Test
	void rejectsLatitudeOutsideRange() throws Exception {
		mockMvc.perform(get("/api/v1/places/reverse-geocode")
				.param("latitude", "90.1")
				.param("longitude", "126.642"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsLongitudeOutsideRange() throws Exception {
		mockMvc.perform(get("/api/v1/places/reverse-geocode")
				.param("latitude", "37.533")
				.param("longitude", "-180.1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsNonNumericCoordinate() throws Exception {
		mockMvc.perform(get("/api/v1/places/reverse-geocode")
				.param("latitude", "not-a-number")
				.param("longitude", "126.642"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("위도는 숫자 형식이어야 합니다."));
	}

	private NaverReverseGeocodeResponse responseWith(Result... results) {
		return new NaverReverseGeocodeResponse(
			new NaverReverseGeocodeResponse.Status(0, "ok", "done"),
			List.of(results)
		);
	}
}
