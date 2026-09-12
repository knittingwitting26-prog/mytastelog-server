package com.mytastelog.server.archive.dto;

import java.util.List;

import com.mytastelog.server.archive.dto.ArchiveRequests.CreateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRevisitIntentRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateWishlistRequest;
import com.mytastelog.server.diary.DiaryTheme;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class ArchiveImportContract {
	private ArchiveImportContract() {}

	public record ImportDiary(@NotBlank String transferId, @NotBlank @Size(max = 512) String sourceLocalId,
		@NotBlank String entity, @NotBlank String name, @NotNull DiaryTheme theme, boolean active) {}
	public record ImportRecord(@NotBlank String transferId, @NotBlank @Size(max = 512) String sourceLocalId,
		@NotBlank String entity, @NotNull @Valid CreateRecordRequest payload) {}
	public record ImportWishlist(@NotBlank String transferId, @NotBlank @Size(max = 512) String sourceLocalId,
		@NotBlank String entity, @NotNull @Valid CreateWishlistRequest payload) {}
	public record ImportRevisit(@NotBlank String transferId, @NotBlank @Size(max = 512) String sourceLocalId,
		@NotBlank String entity, @NotNull @Valid CreateRevisitIntentRequest payload) {}
	public record ImportCollection(@NotBlank String transferId, @NotBlank @Size(max = 512) String sourceLocalId,
		@NotBlank String entity, @NotNull @Valid CreateCollectionRequest payload) {}
	public record ImportRelation(@NotBlank String transferId, @NotBlank @Size(max = 512) String sourceLocalId,
		@NotBlank String entity, @NotBlank String collectionLocalId, @NotBlank String itemLocalId) {}
	public record ImportPhoto(@NotBlank String transferId, @NotBlank @Size(max = 512) String sourceLocalId,
		@NotBlank String entity, @NotBlank String localPhotoRef) {}

	public record ArchiveImportRequest(@NotBlank String contractVersion,
		@NotBlank @Size(max = 128) String transferId, @Positive int sourceSchemaVersion,
		String activeDiaryLocalId,
		@NotNull List<@Valid ImportDiary> diaries,
		@NotNull List<@Valid ImportRecord> records,
		@NotNull List<@Valid ImportWishlist> wishlist,
		@NotNull List<@Valid ImportRevisit> revisits,
		@NotNull List<@Valid ImportCollection> collections,
		@NotNull List<@Valid ImportRelation> relations,
		@NotNull List<@Valid ImportPhoto> photos) {}

	public record ArchiveImportItemResult(String transferId, String sourceLocalId, String entity,
		String status, String serverId, String reason) {}
	public record ArchiveImportResponse(String contractVersion, String transferId, String status,
		List<ArchiveImportItemResult> items, boolean cleanupAllowed) {}
}
