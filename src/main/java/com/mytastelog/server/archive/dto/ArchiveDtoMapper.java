package com.mytastelog.server.archive.dto;

import java.util.List;

import com.mytastelog.server.archive.dto.ArchiveResponses.CollectionResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.DiaryResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.RecordResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.RecordMenuResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.WishlistResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.RevisitIntentResponse;
import com.mytastelog.server.collection.CollectionEntity;
import com.mytastelog.server.diary.DiaryEntity;
import com.mytastelog.server.record.RecordEntity;
import com.mytastelog.server.record.RecordMenuEntity;
import com.mytastelog.server.wishlist.WishlistEntity;
import com.mytastelog.server.revisit.RevisitIntentEntity;

public final class ArchiveDtoMapper {
	private ArchiveDtoMapper() {
	}

	public static DiaryResponse diary(DiaryEntity entity) {
		return new DiaryResponse(entity.getId(), entity.getOwner().getId(), entity.getName(), entity.getTheme(),
			entity.getCreatedAt(), entity.getUpdatedAt());
	}

	public static RecordResponse record(RecordEntity entity, List<RecordMenuEntity> menus) {
		return new RecordResponse(entity.getId(), entity.getOwner().getId(), entity.getDiary().getId(),
			entity.getPlaceId(), entity.getPlaceName(), entity.getCategory(), entity.getDateDisplay(), entity.getMemo(),
			entity.getAddress(), entity.getLatitude(), entity.getLongitude(), entity.getRating(), entity.getMenu(), entity.getPrice(), entity.getNote(),
			entity.getPhotoReference(), entity.getCreatedAt(), entity.getUpdatedAt(), "record",
			entity.getVisibility(), entity.getVisitAt(), menus.stream().map(ArchiveDtoMapper::recordMenu).toList());
	}

	public static RecordMenuResponse recordMenu(RecordMenuEntity entity) {
		String photoUrl = entity.getPhotoReference() == null ? null
			: "/api/v1/records/" + entity.getRecord().getId() + "/menus/" + entity.getId() + "/photo";
		return new RecordMenuResponse(entity.getId(), entity.getName(), entity.getPrice(), entity.getPosition(), photoUrl);
	}

	public static WishlistResponse wishlist(WishlistEntity entity) {
		return new WishlistResponse(entity.getId(), entity.getOwner().getId(), entity.getDiary().getId(),
			entity.getPlaceId(), entity.getPlaceName(), entity.getCategory(), entity.getDateDisplay(), entity.getMemo(),
			entity.getAddress(), entity.getLatitude(), entity.getLongitude(), entity.getRating(), entity.getMenu(), entity.getPrice(), entity.getNote(),
			entity.getPhotoReference(), entity.getCreatedAt(), entity.getUpdatedAt(), "wishlist");
	}

	public static CollectionResponse collection(CollectionEntity entity, List<String> itemIds) {
		return new CollectionResponse(entity.getId(), entity.getOwner().getId(), entity.getDiary().getId(),
			entity.getName(), entity.getMemo(), List.copyOf(itemIds), entity.getPhotoReference(),
			entity.getCreatedAt(), entity.getUpdatedAt());
	}

	public static RevisitIntentResponse revisit(RevisitIntentEntity entity) {
		return new RevisitIntentResponse(entity.getId(), entity.getOwner().getId(), entity.getDiary().getId(),
			entity.getPlaceId(), entity.getCreatedAt());
	}
}
