package com.mytastelog.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mytastelog.server.dto.PlaceSearchResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.ApiSuccess;
import com.mytastelog.server.service.PlaceSearchService;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceSearchController {

	private final PlaceSearchService placeSearchService;

	public PlaceSearchController(PlaceSearchService placeSearchService) {
		this.placeSearchService = placeSearchService;
	}

	@GetMapping("/search")
	public ApiSuccess<PlaceSearchResponse> search(@RequestParam(required = false) String query) {
		return new ApiSuccess<>(placeSearchService.search(query));
	}
}
