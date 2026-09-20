package com.mytastelog.server.archive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mytastelog.server.archive.dto.ArchiveImportContract.ArchiveImportItemResult;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ArchiveImportRequest;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ArchiveImportResponse;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ImportCollection;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ImportDiary;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ImportRecord;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ImportRelation;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ImportRevisit;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ImportWishlist;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRevisitIntentRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveResponses.ArchiveResponse;
import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;

@Service
public class ArchiveImportService {
	private static final String IMPORTED = "IMPORTED";
	private static final String ALREADY_IMPORTED = "ALREADY_IMPORTED";
	private static final String SKIPPED = "SKIPPED_BY_DOMAIN_RULE";
	private final ArchiveService archives;
	private final ArchiveImportItemRepository importItems;

	public ArchiveImportService(ArchiveService archives, ArchiveImportItemRepository importItems) {
		this.archives = archives;
		this.importItems = importItems;
	}

	@Transactional
	public ArchiveImportResponse importArchive(String accountId, ArchiveImportRequest request) {
		if (!"v1".equals(request.contractVersion())) throw validation("지원하지 않는 transfer contract입니다.", "contractVersion");
		validateSources(request);
		List<ArchiveImportItemResult> results = new ArrayList<>();
		Map<String, String> diaryIds = new HashMap<>();
		Map<String, String> itemIds = new HashMap<>();
		Map<String, String> collectionIds = new HashMap<>();

		ArchiveResponse initial = archives.loadArchive(accountId);
		String reusableDiaryId = initial.diaries().isEmpty() ? null : initial.diaries().getFirst().id();
		for (ImportDiary source : request.diaries()) {
			var prior = prior(accountId, request.transferId(), "diary", source.sourceLocalId());
			if (prior != null) {
				diaryIds.put(source.sourceLocalId(), prior.getServerId());
				results.add(replay(request.transferId(), source.sourceLocalId(), "diary", prior));
				continue;
			}
			String serverId = source.active() && reusableDiaryId != null ? reusableDiaryId : serverId(accountId, request.transferId(), "diary", source.sourceLocalId());
			if (!(source.active() && reusableDiaryId != null)) archives.createDiary(accountId, new CreateDiaryRequest(serverId, source.name(), source.theme()));
			diaryIds.put(source.sourceLocalId(), serverId);
			remember(accountId, request.transferId(), "diary", source.sourceLocalId(), serverId, IMPORTED);
			results.add(result(request.transferId(), source.sourceLocalId(), "diary", IMPORTED, serverId, null));
		}

		for (ImportRecord source : request.records()) {
			var prior = prior(accountId, request.transferId(), "record", source.sourceLocalId());
			if (prior != null) { itemIds.put(source.sourceLocalId(), prior.getServerId()); results.add(replay(request.transferId(), source.sourceLocalId(), "record", prior)); continue; }
			CreateRecordRequest local = source.payload();
			String diaryId = requireMapping(diaryIds, local.diaryId(), "records.diaryId");
			String id = serverId(accountId, request.transferId(), "record", source.sourceLocalId());
			var created = archives.createRecord(accountId, new CreateRecordRequest(id, diaryId, "record", local.placeId(), local.placeName(), local.category(), local.date(), local.memo(), local.address(), local.latitude(), local.longitude(), local.visibility(), local.visitAt(), local.rating(), local.menu(), local.price(), local.note(), null, local.menus()));
			itemIds.put(source.sourceLocalId(), created.value().id());
			remember(accountId, request.transferId(), "record", source.sourceLocalId(), created.value().id(), IMPORTED);
			results.add(result(request.transferId(), source.sourceLocalId(), "record", IMPORTED, created.value().id(), null));
		}

		for (ImportWishlist source : request.wishlist()) {
			var prior = prior(accountId, request.transferId(), "wishlist", source.sourceLocalId());
			if (prior != null) { if (prior.getServerId() != null) itemIds.put(source.sourceLocalId(), prior.getServerId()); results.add(replay(request.transferId(), source.sourceLocalId(), "wishlist", prior)); continue; }
			CreateWishlistRequest local = source.payload();
			String diaryId = requireMapping(diaryIds, local.diaryId(), "wishlist.diaryId");
			ArchiveResponse current = archives.loadArchive(accountId);
			boolean visited = current.records().stream().anyMatch(item -> item.diaryId().equals(diaryId) && item.placeId().equals(local.placeId()));
			var duplicate = current.wishlist().stream().filter(item -> item.diaryId().equals(diaryId) && item.placeId().equals(local.placeId())).findFirst();
			if (visited || duplicate.isPresent()) {
				String serverId = duplicate.map(item -> item.id()).orElse(null);
				remember(accountId, request.transferId(), "wishlist", source.sourceLocalId(), serverId, SKIPPED);
				results.add(result(request.transferId(), source.sourceLocalId(), "wishlist", SKIPPED, serverId, visited ? "RECORD_ALREADY_EXISTS" : "WISHLIST_ALREADY_EXISTS"));
				continue;
			}
			String id = serverId(accountId, request.transferId(), "wishlist", source.sourceLocalId());
			var created = archives.createWishlist(accountId, new CreateWishlistRequest(id, diaryId, "wishlist", local.placeId(), local.placeName(), local.category(), local.date(), local.memo(), local.address(), local.latitude(), local.longitude(), local.rating(), local.menu(), local.price(), local.note(), null));
			itemIds.put(source.sourceLocalId(), created.value().id());
			remember(accountId, request.transferId(), "wishlist", source.sourceLocalId(), created.value().id(), IMPORTED);
			results.add(result(request.transferId(), source.sourceLocalId(), "wishlist", IMPORTED, created.value().id(), null));
		}

		for (ImportRevisit source : request.revisits()) {
			var prior = prior(accountId, request.transferId(), "revisit", source.sourceLocalId());
			if (prior != null) { results.add(replay(request.transferId(), source.sourceLocalId(), "revisit", prior)); continue; }
			CreateRevisitIntentRequest local = source.payload();
			String diaryId = requireMapping(diaryIds, local.diaryId(), "revisits.diaryId");
			boolean visited = archives.loadArchive(accountId).records().stream().anyMatch(item -> item.diaryId().equals(diaryId) && item.placeId().equals(local.placeId()));
			if (!visited) {
				remember(accountId, request.transferId(), "revisit", source.sourceLocalId(), null, SKIPPED);
				results.add(result(request.transferId(), source.sourceLocalId(), "revisit", SKIPPED, null, "MISSING_SERVER_ENTITY"));
				continue;
			}
			String id = serverId(accountId, request.transferId(), "revisit", source.sourceLocalId());
			var created = archives.createRevisitIntent(accountId, new CreateRevisitIntentRequest(id, diaryId, local.placeId()));
			remember(accountId, request.transferId(), "revisit", source.sourceLocalId(), created.value().id(), IMPORTED);
			results.add(result(request.transferId(), source.sourceLocalId(), "revisit", IMPORTED, created.value().id(), created.created() ? null : "REVISIT_ALREADY_EXISTS"));
		}

		for (ImportCollection source : request.collections()) {
			var prior = prior(accountId, request.transferId(), "collection", source.sourceLocalId());
			if (prior != null) { collectionIds.put(source.sourceLocalId(), prior.getServerId()); results.add(replay(request.transferId(), source.sourceLocalId(), "collection", prior)); continue; }
			CreateCollectionRequest local = source.payload();
			String diaryId = requireMapping(diaryIds, local.diaryId(), "collections.diaryId");
			String id = serverId(accountId, request.transferId(), "collection", source.sourceLocalId());
			var created = archives.createCollection(accountId, new CreateCollectionRequest(id, diaryId, local.name(), local.memo(), List.of()));
			collectionIds.put(source.sourceLocalId(), created.value().id());
			remember(accountId, request.transferId(), "collection", source.sourceLocalId(), created.value().id(), IMPORTED);
			results.add(result(request.transferId(), source.sourceLocalId(), "collection", IMPORTED, created.value().id(), null));
		}

		for (ImportRelation source : request.relations()) {
			var prior = prior(accountId, request.transferId(), "collection-relation", source.sourceLocalId());
			if (prior != null) { results.add(replay(request.transferId(), source.sourceLocalId(), "collection-relation", prior)); continue; }
			String collectionId = collectionIds.get(source.collectionLocalId());
			String itemId = itemIds.get(source.itemLocalId());
			if (collectionId == null || itemId == null) {
				remember(accountId, request.transferId(), "collection-relation", source.sourceLocalId(), null, SKIPPED);
				results.add(result(request.transferId(), source.sourceLocalId(), "collection-relation", SKIPPED, null, "MISSING_SERVER_ENTITY"));
				continue;
			}
			archives.addCollectionItem(accountId, collectionId, itemId);
			String relationId = collectionId + ":" + itemId;
			remember(accountId, request.transferId(), "collection-relation", source.sourceLocalId(), relationId, IMPORTED);
			results.add(result(request.transferId(), source.sourceLocalId(), "collection-relation", IMPORTED, relationId, null));
		}

		request.photos().forEach(photo -> results.add(result(request.transferId(), photo.sourceLocalId(), "photo", "FAILED", null, "PHOTO_UPLOAD_FAILED")));
		boolean complete = request.photos().isEmpty();
		return new ArchiveImportResponse("v1", request.transferId(), complete ? "COMPLETE" : "PARTIAL", List.copyOf(results), complete);
	}

	private void validateSources(ArchiveImportRequest request) {
		request.diaries().forEach(item -> validateSource(request.transferId(), item.transferId(), item.entity(), "diary"));
		request.records().forEach(item -> validateSource(request.transferId(), item.transferId(), item.entity(), "record"));
		request.wishlist().forEach(item -> validateSource(request.transferId(), item.transferId(), item.entity(), "wishlist"));
		request.revisits().forEach(item -> validateSource(request.transferId(), item.transferId(), item.entity(), "revisit"));
		request.collections().forEach(item -> validateSource(request.transferId(), item.transferId(), item.entity(), "collection"));
		request.relations().forEach(item -> validateSource(request.transferId(), item.transferId(), item.entity(), "collection-relation"));
		request.photos().forEach(item -> validateSource(request.transferId(), item.transferId(), item.entity(), "photo"));
	}

	private void validateSource(String expectedTransfer, String actualTransfer, String actualEntity, String expectedEntity) {
		if (!expectedTransfer.equals(actualTransfer)) throw validation("source transferId가 일치하지 않습니다.", "transferId");
		if (!expectedEntity.equals(actualEntity)) throw validation("source entity가 올바르지 않습니다.", "entity");
	}

	private ArchiveImportItemEntity prior(String accountId, String transferId, String entity, String localId) {
		return importItems.findByAccountIdAndTransferIdAndSourceEntityAndSourceLocalId(accountId, transferId, entity, localId).orElse(null);
	}

	private void remember(String accountId, String transferId, String entity, String localId, String serverId, String status) {
		importItems.saveAndFlush(new ArchiveImportItemEntity(accountId, transferId, entity, localId, serverId, status));
	}

	private ArchiveImportItemResult replay(String transferId, String localId, String entity, ArchiveImportItemEntity prior) {
		String status = IMPORTED.equals(prior.getStatus()) ? ALREADY_IMPORTED : prior.getStatus();
		return result(transferId, localId, entity, status, prior.getServerId(), SKIPPED.equals(status) ? "MISSING_SERVER_ENTITY" : null);
	}

	private ArchiveImportItemResult result(String transferId, String localId, String entity, String status, String serverId, String reason) {
		return new ArchiveImportItemResult(transferId, localId, entity, status, serverId, reason);
	}

	private String requireMapping(Map<String, String> mappings, String localId, String field) {
		String value = mappings.get(localId);
		if (value == null) throw validation("연결된 local ID를 찾을 수 없습니다.", field);
		return value;
	}

	private String serverId(String accountId, String transferId, String entity, String localId) {
		return UUID.nameUUIDFromBytes((accountId + "\0" + transferId + "\0" + entity + "\0" + localId).getBytes(StandardCharsets.UTF_8)).toString();
	}

	private ApiException validation(String message, String field) {
		return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, message, field);
	}
}
