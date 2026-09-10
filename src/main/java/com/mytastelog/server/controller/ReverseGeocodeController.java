package com.mytastelog.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mytastelog.server.dto.ReverseGeocodeResponse;
import com.mytastelog.server.service.ReverseGeocodeService;

@RestController
@RequestMapping("/api/v1/places")
public class ReverseGeocodeController {

	private final ReverseGeocodeService reverseGeocodeService;

	public ReverseGeocodeController(ReverseGeocodeService reverseGeocodeService) {
		this.reverseGeocodeService = reverseGeocodeService;
	}

	@GetMapping("/reverse-geocode")
	public ReverseGeocodeResponse reverseGeocode(
		@RequestParam(required = false) String latitude,
		@RequestParam(required = false) String longitude
	) {
		return reverseGeocodeService.reverseGeocode(latitude, longitude);
	}
}
