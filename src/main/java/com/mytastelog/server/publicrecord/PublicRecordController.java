package com.mytastelog.server.publicrecord;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mytastelog.server.archive.dto.ArchiveResponses.ApiSuccess;
import com.mytastelog.server.photo.PhotoService;
import com.mytastelog.server.publicrecord.PublicRecordResponses.PublicPlaceSummary;
import com.mytastelog.server.publicrecord.PublicRecordResponses.PublicRecordDetail;

@RestController
@RequestMapping("/api/v1/public/records")
public class PublicRecordController {
	private final PublicRecordService publicRecords;
	private final PhotoService photos;

	public PublicRecordController(PublicRecordService publicRecords, PhotoService photos) {
		this.publicRecords = publicRecords;
		this.photos = photos;
	}

	@GetMapping
	ApiSuccess<List<PublicPlaceSummary>> list(
		@RequestParam(required = false) BigDecimal north, @RequestParam(required = false) BigDecimal south,
		@RequestParam(required = false) BigDecimal east, @RequestParam(required = false) BigDecimal west,
		@RequestParam(required = false) Integer limit) {
		return new ApiSuccess<>(publicRecords.list(north, south, east, west, limit));
	}

	@GetMapping("/{id}")
	ApiSuccess<PublicRecordDetail> detail(@PathVariable String id) {
		return new ApiSuccess<>(publicRecords.detail(id));
	}

	@GetMapping("/{id}/photo")
	ResponseEntity<byte[]> photo(@PathVariable String id) {
		var content = photos.readPublicRecord(publicRecords.requirePublic(id));
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType()))
			.cacheControl(CacheControl.noStore()).contentLength(content.bytes().length).body(content.bytes());
	}
}
