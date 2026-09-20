package com.mytastelog.server.archive;

import static com.mytastelog.server.archive.dto.ArchiveDtoMapper.collection;
import static com.mytastelog.server.archive.dto.ArchiveDtoMapper.diary;
import static com.mytastelog.server.archive.dto.ArchiveDtoMapper.wishlist;
import static com.mytastelog.server.archive.dto.ArchiveDtoMapper.revisit;

import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.archive.dto.ArchiveRequests.ConvertWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRevisitIntentRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.ReplaceCollectionOrderRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.RecordMenuRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.UpdateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.UpdateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.UpdateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.UpdateWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveResponses.ArchiveResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.CollectionResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.ConvertWishlistResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.DiaryResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.RecordResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.WishlistResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.RevisitIntentResponse;
import com.mytastelog.server.collection.ArchiveItemType;
import com.mytastelog.server.collection.CollectionEntity;
import com.mytastelog.server.collection.CollectionItemEntity;
import com.mytastelog.server.collection.CollectionItemRepository;
import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.diary.DiaryEntity;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;
import com.mytastelog.server.record.RecordEntity;
import com.mytastelog.server.record.RecordMenuEntity;
import com.mytastelog.server.record.RecordMenuRepository;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordVisibility;
import com.mytastelog.server.photo.PhotoService;
import com.mytastelog.server.wishlist.WishlistEntity;
import com.mytastelog.server.wishlist.WishlistRepository;
import com.mytastelog.server.revisit.RevisitIntentEntity;
import com.mytastelog.server.revisit.RevisitIntentRepository;

@Service
public class ArchiveService {
	private final AccountRepository accounts;
	private final DiaryRepository diaries;
	private final RecordRepository records;
	private final RecordMenuRepository recordMenus;
	private final WishlistRepository wishlistItems;
	private final CollectionRepository collections;
	private final CollectionItemRepository collectionItems;
	private final GlobalItemIdRepository globalItemIds;
	private final RevisitIntentRepository revisitIntents;
	private final PhotoService photos;

	public ArchiveService(AccountRepository accounts, DiaryRepository diaries, RecordRepository records,
		RecordMenuRepository recordMenus,
		WishlistRepository wishlistItems, CollectionRepository collections,
		CollectionItemRepository collectionItems, GlobalItemIdRepository globalItemIds,
		RevisitIntentRepository revisitIntents, PhotoService photos) {
		this.accounts = accounts;
		this.diaries = diaries;
		this.records = records;
		this.recordMenus = recordMenus;
		this.wishlistItems = wishlistItems;
		this.collections = collections;
		this.collectionItems = collectionItems;
		this.globalItemIds = globalItemIds;
		this.revisitIntents = revisitIntents;
		this.photos = photos;
	}

	@Transactional(readOnly = true)
	public ArchiveResponse loadArchive(String accountId) {
		List<DiaryResponse> diaryResponses = diaries.findAllByOwner_IdOrderByCreatedAtAsc(accountId).stream()
			.map(com.mytastelog.server.archive.dto.ArchiveDtoMapper::diary).toList();
		List<RecordResponse> recordResponses = records.findAllByOwner_IdOrderByVisitAtDesc(accountId).stream()
			.map(this::toRecord).toList();
		List<WishlistResponse> wishlistResponses = wishlistItems.findAllByOwner_IdOrderByCreatedAtAsc(accountId).stream()
			.map(com.mytastelog.server.archive.dto.ArchiveDtoMapper::wishlist).toList();
		List<CollectionResponse> collectionResponses = collections.findAllByOwner_IdOrderByDiary_IdAscPositionAsc(accountId)
			.stream().map(this::toCollection).toList();
		List<RevisitIntentResponse> revisitResponses = revisitIntents.findAllByOwner_IdOrderByCreatedAtAsc(accountId)
			.stream().map(com.mytastelog.server.archive.dto.ArchiveDtoMapper::revisit).toList();
		return new ArchiveResponse("v1", Instant.now(), diaryResponses, recordResponses,
			wishlistResponses, collectionResponses, revisitResponses);
	}

	@Transactional(readOnly = true)
	public List<DiaryResponse> listDiaries(String accountId) {
		return diaries.findAllByOwner_IdOrderByCreatedAtAsc(accountId).stream()
			.map(com.mytastelog.server.archive.dto.ArchiveDtoMapper::diary).toList();
	}

	@Transactional
	public CreateResult<DiaryResponse> createDiary(String accountId, CreateDiaryRequest request) {
		var existing = diaries.findById(request.id());
		if (existing.isPresent()) {
			DiaryEntity entity = existing.get();
			if (entity.getOwner().getId().equals(accountId) && entity.getName().equals(request.name())
				&& entity.getTheme() == request.theme()) return new CreateResult<>(diary(entity), false);
			throw conflict("이미 사용 중인 Diary ID입니다.", "id");
		}
		AccountEntity owner = requireAccount(accountId);
		DiaryEntity entity = diaries.saveAndFlush(new DiaryEntity(request.id(), owner, request.name(), request.theme()));
		return new CreateResult<>(diary(entity), true);
	}

	@Transactional
	public DiaryResponse updateDiary(String accountId, String id, UpdateDiaryRequest request) {
		DiaryEntity entity = requireDiary(accountId, id);
		if (request.namePresent() && (request.name() == null || request.name().isBlank()))
			throw validation("Diary 이름은 필수입니다.", "name");
		if (request.themePresent() && request.theme() == null)
			throw validation("Diary 테마는 필수입니다.", "theme");
		entity.update(request.namePresent() ? request.name() : entity.getName(),
			request.themePresent() ? request.theme() : entity.getTheme());
		diaries.saveAndFlush(entity);
		return diary(entity);
	}

	@Transactional
	public CreateResult<RecordResponse> createRecord(String accountId, CreateRecordRequest request) {
		requireLiteral(request.type(), "record", "type");
		validateCoordinates(request.latitude(), request.longitude());
		List<MenuValue> menus = request.menus() == null
			? legacyMenus(request.id(), request.menu(), request.price()) : normalizeMenus(request.menus());
		DiaryEntity diary = requireDiaryForUpdate(accountId, request.diaryId());
		var existingRecord = records.findById(request.id());
		if (existingRecord.isPresent()) {
			RecordEntity entity = existingRecord.get();
			if (sameRecord(entity, accountId, request, menus)) return new CreateResult<>(toRecord(entity), false);
			throw itemIdConflict();
		}
		if (wishlistItems.existsById(request.id())) throw itemIdConflict();
		registerItemId(request.id(), diary.getOwner(), ArchiveItemType.RECORD);
		MenuProjection projection = project(menus);
		RecordEntity entity = records.saveAndFlush(new RecordEntity(request.id(), diary.getOwner(), diary,
			request.placeId(), request.placeName(), request.category(), request.date(), request.memo(), request.address(),
			request.latitude(), request.longitude(), request.rating(), projection.name(), projection.price(), request.note(),
			request.photo(), request.visibility(), request.visitAt()));
		replaceMenus(entity, menus);
		consumeWishlistForPlace(accountId, entity);
		return new CreateResult<>(toRecord(entity), true);
	}

	@Transactional
	public RecordResponse updateRecord(String accountId, String id, UpdateRecordRequest request) {
		RecordEntity entity = requireRecord(accountId, id);
		if (request.placeNamePresent() && (request.placeName() == null || request.placeName().isBlank()))
			throw validation("장소 이름은 필수입니다.", "placeName");
		if (request.memoPresent() && request.memo() == null) throw validation("메모는 null일 수 없습니다.", "memo");
		if (request.visibilityPresent() && request.visibility() == null)
			throw validation("공개 범위는 필수입니다.", "visibility");
		if (request.visitAtPresent() && request.visitAt() == null)
			throw validation("방문일은 필수입니다.", "visitAt");
		validateRating(request.rating(), request.ratingPresent());
		if (request.pricePresent() && request.price() != null && request.price() < 0)
			throw validation("가격은 0 이상이어야 합니다.", "price");
		List<MenuValue> replacement = null;
		if (request.menusPresent()) {
			if (request.menus() == null) throw validation("menus는 null일 수 없습니다.", "menus");
			replacement = normalizeMenus(request.menus());
		} else if (request.menuPresent() || request.pricePresent()) {
			replacement = legacyMenus(entity.getId(),
				request.menuPresent() ? request.menu() : entity.getMenu(),
				request.pricePresent() ? request.price() : entity.getPrice());
		}
		MenuProjection projection = replacement == null
			? new MenuProjection(entity.getMenu(), entity.getPrice()) : project(replacement);
		entity.update(request.placeNamePresent() ? request.placeName() : entity.getPlaceName(),
			request.memoPresent() ? request.memo() : entity.getMemo(),
			request.visibilityPresent() ? request.visibility() : entity.getVisibility(),
			request.visitAtPresent() ? request.visitAt() : entity.getVisitAt(),
			request.ratingPresent() ? request.rating() : entity.getRating(),
			projection.name(), projection.price());
		records.saveAndFlush(entity);
		if (replacement != null) replaceMenus(entity, replacement);
		return toRecord(entity);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void deleteRecord(String accountId, String id) {
		RecordEntity entity = requireRecord(accountId, id);
		String diaryId = entity.getDiary().getId();
		requireDiaryForUpdate(accountId, diaryId);
		String placeId = entity.getPlaceId();
		String photoReference = entity.getPhotoReference();
		cleanupRelations(id);
		records.delete(entity);
		globalItemIds.deleteById(id);
		records.flush();
		if (!records.existsByOwner_IdAndDiary_IdAndPlaceId(accountId, diaryId, placeId)) {
			revisitIntents.deleteAllByOwner_IdAndDiary_IdAndPlaceId(accountId, diaryId, placeId);
		}
		photos.cleanupAfterCommit(photoReference);
	}

	@Transactional
	public CreateResult<WishlistResponse> createWishlist(String accountId, CreateWishlistRequest request) {
		requireLiteral(request.type(), "wishlist", "type");
		validateCoordinates(request.latitude(), request.longitude());
		DiaryEntity diary = requireDiaryForUpdate(accountId, request.diaryId());
		if (records.existsByOwner_IdAndDiary_IdAndPlaceId(accountId, request.diaryId(), request.placeId()))
			throw conflict("이미 방문한 장소는 가보고 싶은 곳에 저장할 수 없습니다.", "placeId");
		var existingWishlist = wishlistItems.findById(request.id());
		if (existingWishlist.isPresent()) {
			WishlistEntity entity = existingWishlist.get();
			if (sameWishlist(entity, accountId, request)) return new CreateResult<>(wishlist(entity), false);
			throw itemIdConflict();
		}
		if (records.existsById(request.id())) throw itemIdConflict();
		registerItemId(request.id(), diary.getOwner(), ArchiveItemType.WISHLIST);
		WishlistEntity entity = wishlistItems.saveAndFlush(new WishlistEntity(request.id(), diary.getOwner(), diary,
			request.placeId(), request.placeName(), request.category(), request.date(), request.memo(), request.address(),
			request.latitude(), request.longitude(), request.rating(), request.menu(), request.price(), request.note(), request.photo()));
		return new CreateResult<>(wishlist(entity), true);
	}

	@Transactional
	public WishlistResponse updateWishlist(String accountId, String id, UpdateWishlistRequest request) {
		WishlistEntity entity = requireWishlist(accountId, id);
		if (request.placeNamePresent() && (request.placeName() == null || request.placeName().isBlank()))
			throw validation("장소 이름은 필수입니다.", "placeName");
		if (request.memoPresent() && request.memo() == null) throw validation("메모는 null일 수 없습니다.", "memo");
		entity.update(request.placeNamePresent() ? request.placeName() : entity.getPlaceName(),
			request.memoPresent() ? request.memo() : entity.getMemo());
		wishlistItems.saveAndFlush(entity);
		return wishlist(entity);
	}

	@Transactional
	public void deleteWishlist(String accountId, String id) {
		WishlistEntity entity = requireWishlist(accountId, id);
		String photoReference = entity.getPhotoReference();
		cleanupRelations(id);
		wishlistItems.delete(entity);
		globalItemIds.deleteById(id);
		wishlistItems.flush();
		photos.cleanupAfterCommit(photoReference);
	}

	@Transactional
	public CreateResult<CollectionResponse> createCollection(String accountId, CreateCollectionRequest request) {
		var existing = collections.findById(request.id());
		if (existing.isPresent()) {
			CollectionEntity entity = existing.get();
			if (sameCollection(entity, accountId, request)) return new CreateResult<>(toCollection(entity), false);
			throw conflict("이미 사용 중인 Collection ID입니다.", "id");
		}
		DiaryEntity diary = requireDiary(accountId, request.diaryId());
		List<ResolvedItem> items = resolveDistinctItems(accountId, diary.getId(), request.itemIds());
		int position = collections.findFirstByDiary_IdAndOwner_IdOrderByPositionDesc(diary.getId(), accountId)
			.map(value -> value.getPosition() + 1).orElse(0);
		CollectionEntity entity = collections.saveAndFlush(new CollectionEntity(request.id(), diary.getOwner(), diary,
			request.name(), request.memo(), position));
		for (int index = 0; index < items.size(); index++) {
			ResolvedItem item = items.get(index);
			collectionItems.save(new CollectionItemEntity(entity, item.id(), item.type(), index));
		}
		collectionItems.flush();
		return new CreateResult<>(toCollection(entity), true);
	}

	@Transactional
	public CollectionResponse updateCollection(String accountId, String id, UpdateCollectionRequest request) {
		CollectionEntity entity = requireCollection(accountId, id);
		if (request.namePresent() && (request.name() == null || request.name().isBlank()))
			throw validation("Collection 이름은 필수입니다.", "name");
		if (request.namePresent()) entity.updateName(request.name());
		collections.saveAndFlush(entity);
		return toCollection(entity);
	}

	@Transactional
	public void deleteCollection(String accountId, String id) {
		CollectionEntity entity = requireCollection(accountId, id);
		String photoReference = entity.getPhotoReference();
		collections.delete(entity);
		collections.flush();
		photos.cleanupAfterCommit(photoReference);
	}

	@Transactional
	public CollectionResponse addCollectionItem(String accountId, String collectionId, String itemId) {
		CollectionEntity target = requireCollection(accountId, collectionId);
		ResolvedItem item = resolveItem(accountId, itemId);
		if (!target.getDiary().getId().equals(item.diaryId()))
			throw conflict("같은 Diary의 항목만 Collection에 추가할 수 있습니다.", "itemId");
		if (collectionItems.findById_CollectionIdAndId_ItemId(collectionId, itemId).isPresent())
			throw conflict("이미 Collection에 포함된 항목입니다.", "itemId");
		int position = collectionItems.findByCollection_IdOrderByPosition(collectionId).size();
		collectionItems.saveAndFlush(new CollectionItemEntity(target, itemId, item.type(), position));
		target.touch();
		collections.saveAndFlush(target);
		return toCollection(target);
	}

	@Transactional
	public void removeCollectionItem(String accountId, String collectionId, String itemId) {
		CollectionEntity target = requireCollection(accountId, collectionId);
		CollectionItemEntity relation = collectionItems.findById_CollectionIdAndId_ItemId(collectionId, itemId)
			.orElseThrow(() -> notFound("Collection relation을 찾을 수 없습니다."));
		collectionItems.delete(relation);
		collectionItems.flush();
		normalizeItemOrder(target);
		target.touch();
		collections.saveAndFlush(target);
	}

	@Transactional
	public List<CollectionResponse> replaceCollectionOrder(String accountId, String diaryId,
		ReplaceCollectionOrderRequest request) {
		requireDiary(accountId, diaryId);
		List<CollectionEntity> current = collections.findAllByDiary_IdAndOwner_IdOrderByPositionAsc(diaryId, accountId);
		List<String> requestedIds = request.collectionIds();
		if (new HashSet<>(requestedIds).size() != requestedIds.size())
			throw conflict("Collection 순서에 중복 ID가 있습니다.", "collectionIds");
		Set<String> currentIds = current.stream().map(CollectionEntity::getId).collect(java.util.stream.Collectors.toSet());
		if (requestedIds.size() != current.size() || !currentIds.equals(new HashSet<>(requestedIds)))
			throw conflict("Diary의 모든 Collection ID를 정확히 포함해야 합니다.", "collectionIds");
		java.util.Map<String, CollectionEntity> byId = current.stream()
			.collect(java.util.stream.Collectors.toMap(CollectionEntity::getId, value -> value));
		List<CollectionEntity> ordered = new ArrayList<>();
		for (int index = 0; index < requestedIds.size(); index++) {
			CollectionEntity entity = byId.get(requestedIds.get(index));
			entity.moveTo(current.size() + index);
			ordered.add(entity);
		}
		collections.saveAllAndFlush(ordered);
		for (int index = 0; index < ordered.size(); index++) ordered.get(index).moveTo(index);
		collections.saveAllAndFlush(ordered);
		return ordered.stream().map(this::toCollection).toList();
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ConvertWishlistResponse convertWishlist(String accountId, String wishlistId, ConvertWishlistRequest request) {
		WishlistEntity source = requireWishlist(accountId, wishlistId);
		requireDiaryForUpdate(accountId, source.getDiary().getId());
		validateCoordinates(request.latitude(), request.longitude());
		List<MenuValue> menus = request.menus() == null
			? legacyMenus(request.id(),
				request.menu() != null ? request.menu() : source.getMenu(),
				request.price() != null ? request.price() : source.getPrice())
			: normalizeMenus(request.menus());
		MenuProjection projection = project(menus);
		if (records.existsById(request.id()) || wishlistItems.existsById(request.id())) throw itemIdConflict();
		registerItemId(request.id(), source.getOwner(), ArchiveItemType.RECORD);
		List<CollectionItemEntity> memberships = collectionItems.findById_ItemIdOrderByCollection_IdAsc(wishlistId);
		for (CollectionItemEntity membership : memberships) {
			CollectionEntity target = membership.getCollection();
			if (!target.getOwner().getId().equals(accountId) || !target.getDiary().getId().equals(source.getDiary().getId()))
				throw conflict("Wishlist relation의 owner 또는 Diary가 올바르지 않습니다.", "id");
		}
		String transferredPhoto = photos.isManagedReference(source.getPhotoReference())
			? source.getPhotoReference() : request.photo();
		RecordEntity created = records.save(new RecordEntity(request.id(), source.getOwner(), source.getDiary(),
			request.placeId(), request.placeName(), request.category(), request.date(), request.memo(), request.address(),
			request.latitude() != null ? request.latitude() : source.getLatitude(),
			request.longitude() != null ? request.longitude() : source.getLongitude(), request.rating(), projection.name(),
			projection.price(), request.note(), transferredPhoto, request.visibility(), request.visitAt()));
		records.flush();
		replaceMenus(created, menus);
		List<CollectionEntity> changed = new ArrayList<>();
		for (CollectionItemEntity membership : memberships) {
			CollectionEntity target = membership.getCollection();
			if (collectionItems.findById_CollectionIdAndId_ItemId(target.getId(), request.id()).isPresent())
				throw conflict("변환될 Record relation이 이미 존재합니다.", "id");
			int position = membership.getPosition();
			collectionItems.delete(membership);
			collectionItems.flush();
			collectionItems.save(new CollectionItemEntity(target, request.id(), ArchiveItemType.RECORD, position));
			target.touch();
			changed.add(target);
		}
		records.flush();
		collectionItems.flush();
		wishlistItems.delete(source);
		globalItemIds.deleteById(wishlistId);
		wishlistItems.flush();
		collections.saveAllAndFlush(changed);
		consumeWishlistForPlace(accountId, created);
		return new ConvertWishlistResponse(toRecord(created), changed.stream().map(this::toCollection).toList());
	}

	private void consumeWishlistForPlace(String accountId, RecordEntity record) {
		for (WishlistEntity wish : wishlistItems.findAllByOwner_IdOrderByCreatedAtAsc(accountId)) {
			if (!wish.getDiary().getId().equals(record.getDiary().getId()) || !wish.getPlaceId().equals(record.getPlaceId())) continue;
			String wishlistPhoto = wish.getPhotoReference();
			if (photos.isManagedReference(wishlistPhoto) && !photos.isManagedReference(record.getPhotoReference())) {
				record.setPhotoReference(wishlistPhoto);
			}
			for (CollectionItemEntity membership : collectionItems.findById_ItemIdOrderByCollection_IdAsc(wish.getId())) {
				CollectionEntity target = membership.getCollection();
				int position = membership.getPosition();
				collectionItems.delete(membership);
				collectionItems.flush();
				if (collectionItems.findById_CollectionIdAndId_ItemId(target.getId(), record.getId()).isEmpty()) {
					collectionItems.saveAndFlush(new CollectionItemEntity(target, record.getId(), ArchiveItemType.RECORD, position));
				}
				normalizeItemOrder(target);
				target.touch();
			}
			wishlistItems.delete(wish);
			globalItemIds.deleteById(wish.getId());
			if (!Objects.equals(wishlistPhoto, record.getPhotoReference())) photos.cleanupAfterCommit(wishlistPhoto);
		}
		records.flush();
		wishlistItems.flush();
	}

	@Transactional
	public CreateResult<RevisitIntentResponse> createRevisitIntent(String accountId, CreateRevisitIntentRequest request) {
		DiaryEntity diary = requireDiaryForUpdate(accountId, request.diaryId());
		var existing = revisitIntents.findByOwner_IdAndDiary_IdAndPlaceId(accountId, request.diaryId(), request.placeId());
		if (existing.isPresent()) return new CreateResult<>(revisit(existing.get()), false);
		if (revisitIntents.existsById(request.id())) throw conflict("이미 사용 중인 Revisit ID입니다.", "id");
		if (!records.existsByOwner_IdAndDiary_IdAndPlaceId(accountId, request.diaryId(), request.placeId()))
			throw conflict("방문 기록이 있는 장소만 또 가고 싶은 곳에 저장할 수 있습니다.", "placeId");
		try {
			return new CreateResult<>(revisit(revisitIntents.saveAndFlush(new RevisitIntentEntity(request.id(), diary.getOwner(), diary, request.placeId()))), true);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("이미 또 가고 싶은 곳에 저장된 장소입니다.", "placeId");
		}
	}

	@Transactional
	public void deleteRevisitIntent(String accountId, String id) {
		RevisitIntentEntity entity = revisitIntents.findByIdAndOwner_Id(id, accountId)
			.orElseThrow(() -> notFound("또 가고 싶은 곳을 찾을 수 없습니다."));
		revisitIntents.delete(entity);
	}

	private AccountEntity requireAccount(String accountId) {
		return accounts.findById(accountId).orElseThrow(() -> notFound("Account를 찾을 수 없습니다."));
	}

	private DiaryEntity requireDiary(String accountId, String id) {
		validateResourceId(id, "id");
		return diaries.findByIdAndOwner_Id(id, accountId).orElseThrow(() -> notFound("Diary를 찾을 수 없습니다."));
	}

	private DiaryEntity requireDiaryForUpdate(String accountId, String id) {
		validateResourceId(id, "id");
		return diaries.findByIdAndOwnerIdForUpdate(id, accountId)
			.orElseThrow(() -> notFound("Diary를 찾을 수 없습니다."));
	}

	private RecordEntity requireRecord(String accountId, String id) {
		validateResourceId(id, "id");
		return records.findByIdAndOwner_Id(id, accountId).orElseThrow(() -> notFound("Record를 찾을 수 없습니다."));
	}

	private WishlistEntity requireWishlist(String accountId, String id) {
		validateResourceId(id, "id");
		return wishlistItems.findByIdAndOwner_Id(id, accountId).orElseThrow(() -> notFound("Wishlist를 찾을 수 없습니다."));
	}

	private CollectionEntity requireCollection(String accountId, String id) {
		validateResourceId(id, "id");
		return collections.findByIdAndOwner_Id(id, accountId).orElseThrow(() -> notFound("Collection을 찾을 수 없습니다."));
	}

	private ResolvedItem resolveItem(String accountId, String itemId) {
		validateResourceId(itemId, "itemId");
		var foundRecord = records.findByIdAndOwner_Id(itemId, accountId);
		var foundWishlist = wishlistItems.findByIdAndOwner_Id(itemId, accountId);
		if (foundRecord.isPresent() && foundWishlist.isPresent()) throw itemIdConflict();
		if (foundRecord.isPresent()) return new ResolvedItem(itemId, foundRecord.get().getDiary().getId(), ArchiveItemType.RECORD);
		if (foundWishlist.isPresent()) return new ResolvedItem(itemId, foundWishlist.get().getDiary().getId(), ArchiveItemType.WISHLIST);
		throw notFound("Archive item을 찾을 수 없습니다.");
	}

	private List<ResolvedItem> resolveDistinctItems(String accountId, String diaryId, List<String> itemIds) {
		if (new HashSet<>(itemIds).size() != itemIds.size())
			throw conflict("Collection itemIds에 중복 ID가 있습니다.", "itemIds");
		List<ResolvedItem> result = new ArrayList<>();
		for (String itemId : itemIds) {
			ResolvedItem item = resolveItem(accountId, itemId);
			if (!diaryId.equals(item.diaryId()))
				throw conflict("같은 Diary의 항목만 Collection에 포함할 수 있습니다.", "itemIds");
			result.add(item);
		}
		return result;
	}

	private void cleanupRelations(String itemId) {
		List<CollectionItemEntity> relations = collectionItems.findById_ItemIdOrderByCollection_IdAsc(itemId);
		Set<CollectionEntity> affected = relations.stream().map(CollectionItemEntity::getCollection)
			.collect(java.util.stream.Collectors.toSet());
		collectionItems.deleteAll(relations);
		collectionItems.flush();
		for (CollectionEntity entity : affected) {
			normalizeItemOrder(entity);
			entity.touch();
		}
		collections.saveAll(affected);
	}

	private void normalizeItemOrder(CollectionEntity collection) {
		List<CollectionItemEntity> remaining = collectionItems.findByCollection_IdOrderByPosition(collection.getId());
		for (int index = 0; index < remaining.size(); index++) remaining.get(index).moveTo(index);
		collectionItems.saveAll(remaining);
	}

	private CollectionResponse toCollection(CollectionEntity entity) {
		List<String> itemIds = collectionItems.findByCollection_IdOrderByPosition(entity.getId()).stream()
			.map(item -> item.getId().getItemId()).toList();
		return collection(entity, itemIds);
	}

	private boolean sameRecord(RecordEntity entity, String accountId, CreateRecordRequest request, List<MenuValue> menus) {
		MenuProjection projection = project(menus);
		return entity.getOwner().getId().equals(accountId) && entity.getDiary().getId().equals(request.diaryId())
			&& entity.getPlaceId().equals(request.placeId()) && entity.getPlaceName().equals(request.placeName())
			&& entity.getCategory().equals(request.category()) && entity.getDateDisplay().equals(request.date())
			&& entity.getMemo().equals(request.memo()) && entity.getAddress().equals(request.address())
			&& decimalEquals(entity.getLatitude(), request.latitude()) && decimalEquals(entity.getLongitude(), request.longitude())
			&& decimalEquals(entity.getRating(), request.rating()) && Objects.equals(entity.getMenu(), projection.name())
			&& Objects.equals(entity.getPrice(), projection.price()) && sameMenus(entity, menus)
			&& Objects.equals(entity.getNote(), request.note())
			&& Objects.equals(entity.getPhotoReference(), request.photo()) && entity.getVisibility() == request.visibility()
			&& entity.getVisitAt().equals(request.visitAt());
	}

	private RecordResponse toRecord(RecordEntity entity) {
		return com.mytastelog.server.archive.dto.ArchiveDtoMapper.record(entity,
			recordMenus.findByRecord_IdOrderByPositionAsc(entity.getId()));
	}

	private List<MenuValue> normalizeMenus(List<RecordMenuRequest> requested) {
		if (requested.size() > 10) throw validation("메뉴는 최대 10개까지 저장할 수 있습니다.", "menus");
		Set<String> ids = new HashSet<>();
		List<MenuValue> normalized = new ArrayList<>();
		for (int index = 0; index < requested.size(); index++) {
			RecordMenuRequest menu = requested.get(index);
			if (menu == null) throw validation("메뉴는 null일 수 없습니다.", "menus");
			if (menu.id() == null || menu.id().isBlank() || menu.id().length() > 128)
				throw validation("메뉴 ID는 128자 이하의 필수 값입니다.", "menus.id");
			if (!ids.add(menu.id())) throw conflict("메뉴 ID가 중복되었습니다.", "menus.id");
			if (menu.name() == null || menu.name().isBlank() || menu.name().length() > 300)
				throw validation("메뉴 이름은 300자 이하의 필수 값입니다.", "menus.name");
			if (menu.price() != null && menu.price() < 0)
				throw validation("메뉴 가격은 0 이상이어야 합니다.", "menus.price");
			normalized.add(new MenuValue(menu.id(), menu.name(), menu.price(), index));
		}
		return normalized;
	}

	private List<MenuValue> legacyMenus(String recordId, String name, Long price) {
		if ((name == null || name.isBlank()) && price == null) return List.of();
		return List.of(new MenuValue(legacyMenuId(recordId), name == null || name.isBlank() ? null : name, price, 0));
	}

	private String legacyMenuId(String recordId) {
		return UUID.nameUUIDFromBytes(("mytastelog:record-menu:" + recordId)
			.getBytes(StandardCharsets.UTF_8)).toString();
	}

	private MenuProjection project(List<MenuValue> menus) {
		return menus.isEmpty() ? new MenuProjection(null, null)
			: new MenuProjection(menus.get(0).name(), menus.get(0).price());
	}

	private boolean sameMenus(RecordEntity entity, List<MenuValue> expected) {
		List<RecordMenuEntity> actual = recordMenus.findByRecord_IdOrderByPositionAsc(entity.getId());
		if (actual.size() != expected.size()) return false;
		for (int index = 0; index < actual.size(); index++) {
			RecordMenuEntity left = actual.get(index);
			MenuValue right = expected.get(index);
			if (!left.getId().equals(right.id()) || !Objects.equals(left.getName(), right.name())
				|| !Objects.equals(left.getPrice(), right.price()) || left.getPosition() != right.position()) return false;
		}
		return true;
	}

	private void replaceMenus(RecordEntity record, List<MenuValue> menus) {
		for (MenuValue menu : menus) {
			recordMenus.findById(menu.id()).ifPresent(existing -> {
				if (!existing.getRecord().getId().equals(record.getId()))
					throw conflict("이미 다른 Record에서 사용 중인 메뉴 ID입니다.", "menus.id");
			});
		}
		recordMenus.deleteAll(recordMenus.findByRecord_IdOrderByPositionAsc(record.getId()));
		recordMenus.flush();
		if (!menus.isEmpty()) {
			recordMenus.saveAll(menus.stream()
				.map(menu -> new RecordMenuEntity(menu.id(), record, menu.name(), menu.price(), menu.position()))
				.toList());
			recordMenus.flush();
		}
	}

	private record MenuValue(String id, String name, Long price, int position) {}
	private record MenuProjection(String name, Long price) {}

	private boolean sameWishlist(WishlistEntity entity, String accountId, CreateWishlistRequest request) {
		return entity.getOwner().getId().equals(accountId) && entity.getDiary().getId().equals(request.diaryId())
			&& entity.getPlaceId().equals(request.placeId()) && entity.getPlaceName().equals(request.placeName())
			&& entity.getCategory().equals(request.category()) && entity.getDateDisplay().equals(request.date())
			&& entity.getMemo().equals(request.memo()) && entity.getAddress().equals(request.address())
			&& decimalEquals(entity.getLatitude(), request.latitude()) && decimalEquals(entity.getLongitude(), request.longitude())
			&& decimalEquals(entity.getRating(), request.rating()) && Objects.equals(entity.getMenu(), request.menu())
			&& Objects.equals(entity.getPrice(), request.price()) && Objects.equals(entity.getNote(), request.note())
			&& Objects.equals(entity.getPhotoReference(), request.photo());
	}

	private boolean sameCollection(CollectionEntity entity, String accountId, CreateCollectionRequest request) {
		return entity.getOwner().getId().equals(accountId) && entity.getDiary().getId().equals(request.diaryId())
			&& entity.getName().equals(request.name()) && entity.getMemo().equals(request.memo())
			&& toCollection(entity).itemIds().equals(request.itemIds());
	}

	private boolean decimalEquals(BigDecimal left, BigDecimal right) {
		return left == null ? right == null : right != null && left.compareTo(right) == 0;
	}

	private void validateRating(BigDecimal rating, boolean present) {
		if (present && rating != null && (rating.compareTo(BigDecimal.ZERO) < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0))
			throw validation("평점은 0 이상 5 이하여야 합니다.", "rating");
	}

	private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
		if ((latitude == null) != (longitude == null))
			throw validation("위도와 경도는 함께 제공해야 합니다.", latitude == null ? "latitude" : "longitude");
		if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0))
			throw validation("위도는 -90 이상 90 이하여야 합니다.", "latitude");
		if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0))
			throw validation("경도는 -180 이상 180 이하여야 합니다.", "longitude");
	}

	private void requireLiteral(String actual, String expected, String field) {
		if (!expected.equals(actual)) throw validation("type은 '" + expected + "'이어야 합니다.", field);
	}

	private void registerItemId(String id, AccountEntity owner, ArchiveItemType type) {
		try {
			globalItemIds.saveAndFlush(new GlobalItemIdEntity(id, owner, type));
		} catch (DataIntegrityViolationException exception) {
			throw itemIdConflict();
		}
	}

	private void validateResourceId(String id, String field) {
		if (id == null || id.isBlank() || id.length() > 128)
			throw validation("Resource ID는 비어 있지 않은 128자 이하 문자열이어야 합니다.", field);
	}

	private ApiException itemIdConflict() {
		return conflict("Record와 Wishlist가 공유하는 item ID가 이미 사용 중입니다.", "id");
	}

	private ApiException validation(String message, String field) {
		return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, message, field);
	}

	private ApiException conflict(String message, String field) {
		return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, message, field);
	}

	private ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, message);
	}

	private record ResolvedItem(String id, String diaryId, ArchiveItemType type) {}
	public record CreateResult<T>(T value, boolean created) {}
}
