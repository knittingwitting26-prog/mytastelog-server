package com.mytastelog.server.publicrecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;
import com.mytastelog.server.photo.PhotoService;
import com.mytastelog.server.publicrecord.PublicRecordResponses.PublicPlaceSummary;
import com.mytastelog.server.publicrecord.PublicRecordResponses.PublicRecordDetail;
import com.mytastelog.server.record.RecordEntity;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordVisibility;

@Service
public class PublicRecordService {
	private static final int DEFAULT_LIMIT = 100;
	private static final int MAX_LIMIT = 200;

	private final RecordRepository records;
	private final PhotoService photos;

	public PublicRecordService(RecordRepository records, PhotoService photos) {
		this.records = records;
		this.photos = photos;
	}

	@Transactional(readOnly = true)
	public List<PublicPlaceSummary> list(BigDecimal north, BigDecimal south, BigDecimal east, BigDecimal west,
		Integer requestedLimit) {
		int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
		if (limit < 1 || limit > MAX_LIMIT) throw validation("limit은 1 이상 200 이하여야 합니다.", "limit");
		boolean hasAnyBound = north != null || south != null || east != null || west != null;
		boolean hasAllBounds = north != null && south != null && east != null && west != null;
		if (hasAnyBound && !hasAllBounds) throw validation("지도 bounds는 north, south, east, west를 모두 제공해야 합니다.", "bounds");
		if (hasAllBounds) validateBounds(north, south, east, west);

		var page = PageRequest.of(0, limit);
		List<RecordEntity> candidates = hasAllBounds
			? records.findPublicInBounds(RecordVisibility.PUBLIC, north, south, east, west, page)
			: records.findAllByVisibilityOrderByCreatedAtDesc(RecordVisibility.PUBLIC, page);
		var places = new LinkedHashMap<String, PlaceAccumulator>();
		for (RecordEntity record : candidates) {
			if (record.getLatitude() == null || record.getLongitude() == null) continue;
			places.computeIfAbsent(record.getPlaceId(), ignored -> new PlaceAccumulator(record, photos.isManagedReference(record.getPhotoReference())))
				.add(record);
		}
		return places.values().stream().map(PlaceAccumulator::response).toList();
	}

	@Transactional(readOnly = true)
	public PublicRecordDetail detail(String id) {
		RecordEntity record = requirePublic(id);
		String photoUrl = photos.isManagedReference(record.getPhotoReference())
			? "/api/v1/public/records/" + record.getId() + "/photo" : null;
		return new PublicRecordDetail(record.getId(), record.getPlaceId(), record.getPlaceName(), record.getAddress(),
			record.getCategory(), record.getLatitude(), record.getLongitude(), record.getRating(), record.getMenu(),
			photoUrl);
	}

	@Transactional(readOnly = true)
	public RecordEntity requirePublic(String id) {
		if (id == null || id.isBlank() || id.length() > 128) throw notFound();
		return records.findById(id).filter(record -> record.getVisibility() == RecordVisibility.PUBLIC)
			.orElseThrow(this::notFound);
	}

	private void validateBounds(BigDecimal north, BigDecimal south, BigDecimal east, BigDecimal west) {
		if (north.compareTo(south) < 0 || north.compareTo(BigDecimal.valueOf(90)) > 0
			|| south.compareTo(BigDecimal.valueOf(-90)) < 0 || east.compareTo(west) < 0
			|| east.compareTo(BigDecimal.valueOf(180)) > 0 || west.compareTo(BigDecimal.valueOf(-180)) < 0) {
			throw validation("지도 bounds 범위가 올바르지 않습니다.", "bounds");
		}
	}

	private ApiException validation(String message, String field) {
		return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, message, field);
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Public Record를 찾을 수 없습니다.");
	}

	private static final class PlaceAccumulator {
		private final RecordEntity representative;
		private final boolean hasPhoto;
		private BigDecimal ratingTotal = BigDecimal.ZERO;
		private long ratingCount;
		private long recordCount;

		private PlaceAccumulator(RecordEntity representative, boolean hasPhoto) {
			this.representative = representative;
			this.hasPhoto = hasPhoto;
		}

		private PlaceAccumulator add(RecordEntity record) {
			recordCount++;
			if (record.getRating() != null) {
				ratingTotal = ratingTotal.add(record.getRating());
				ratingCount++;
			}
			return this;
		}

		private PublicPlaceSummary response() {
			BigDecimal average = ratingCount == 0 ? null : ratingTotal.divide(BigDecimal.valueOf(ratingCount), 1, RoundingMode.HALF_UP);
			return new PublicPlaceSummary(representative.getId(), representative.getPlaceId(), representative.getPlaceName(),
				representative.getAddress(), representative.getCategory(), representative.getLatitude(), representative.getLongitude(),
				average, recordCount, hasPhoto);
		}
	}
}
