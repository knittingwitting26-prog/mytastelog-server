package com.mytastelog.server.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.archive.dto.ArchiveRequests.ConvertWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRevisitIntentRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.ReplaceCollectionOrderRequest;
import com.mytastelog.server.collection.CollectionItemRepository;
import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordVisibility;
import com.mytastelog.server.wishlist.WishlistRepository;
import com.mytastelog.server.revisit.RevisitIntentRepository;

@SpringBootTest(properties = {
	"naver.local.client-id=test-client-id",
	"naver.local.client-secret=test-client-secret"
})
@ActiveProfiles("test")
class ArchiveServiceIntegrationTest {
	@Autowired ArchiveService service;
	@Autowired AccountRepository accounts;
	@Autowired DiaryRepository diaries;
	@Autowired RecordRepository records;
	@Autowired WishlistRepository wishlist;
	@Autowired CollectionRepository collections;
	@Autowired CollectionItemRepository collectionItems;
	@Autowired GlobalItemIdRepository globalItemIds;
	@Autowired RevisitIntentRepository revisitIntents;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		collectionItems.deleteAll();
		collections.deleteAll();
		revisitIntents.deleteAll();
		records.deleteAll();
		wishlist.deleteAll();
		diaries.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void repositoryQueriesAreOwnerScopedAndCollectionsAreOrdered() {
		String ownerA = account();
		String ownerB = account();
		diary(ownerA, "diary-a");
		diary(ownerB, "diary-b");
		service.createCollection(ownerA, collectionRequest("collection-1", "diary-a", List.of()));
		service.createCollection(ownerA, collectionRequest("collection-2", "diary-a", List.of()));

		assertThat(diaries.findByIdAndOwner_Id("diary-a", ownerA)).isPresent();
		assertThat(diaries.findByIdAndOwner_Id("diary-b", ownerA)).isEmpty();
		assertThat(collections.findAllByDiary_IdAndOwner_IdOrderByPositionAsc("diary-a", ownerA))
			.extracting(value -> value.getId()).containsExactly("collection-1", "collection-2");
	}

	@Test
	void recordCreateSupportsReplayAndRejectsBothKindsOfGlobalIdCollisionAndForeignDiary() {
		String ownerA = account();
		String ownerB = account();
		diary(ownerA, "diary-a");
		diary(ownerB, "diary-b");
		CreateRecordRequest request = recordRequest("record-1", "diary-a");

		assertThat(service.createRecord(ownerA, request).created()).isTrue();
		assertThat(service.createRecord(ownerA, request).created()).isFalse();
		assertConflict(() -> service.createRecord(ownerA, recordRequest("record-1", "diary-a", "changed")));
		service.createWishlist(ownerA, wishlistRequest("shared-id", "diary-a"));
		assertConflict(() -> service.createRecord(ownerA, recordRequest("shared-id", "diary-a")));
		assertNotFound(() -> service.createRecord(ownerA, recordRequest("foreign-diary", "diary-b")));
	}

	@Test
	void recordDeleteRemovesEveryRelationAndPreservesCollections() {
		String owner = account();
		diary(owner, "diary-a");
		service.createRecord(owner, recordRequest("record-1", "diary-a"));
		service.createCollection(owner, collectionRequest("collection-a", "diary-a", List.of("record-1")));
		service.createCollection(owner, collectionRequest("collection-b", "diary-a", List.of("record-1")));

		service.deleteRecord(owner, "record-1");

		assertThat(records.findById("record-1")).isEmpty();
		assertThat(collectionItems.findById_ItemIdOrderByCollection_IdAsc("record-1")).isEmpty();
		assertThat(collections.count()).isEqualTo(2);
	}

	@Test
	void wishlistDeleteCleansRelationsAndCollectionDeletePreservesSources() {
		String owner = account();
		diary(owner, "diary-a");
		service.createWishlist(owner, wishlistRequest("wish-delete", "diary-a"));
		service.createRecord(owner, recordRequest("record-keep", "diary-a"));
		service.createCollection(owner, collectionRequest("collection-delete", "diary-a",
			List.of("wish-delete", "record-keep")));

		service.deleteWishlist(owner, "wish-delete");
		assertThat(wishlist.findById("wish-delete")).isEmpty();
		assertThat(collections.findById("collection-delete")).isPresent();
		assertThat(collectionItems.findByCollection_IdOrderByPosition("collection-delete"))
			.extracting(value -> value.getId().getItemId()).containsExactly("record-keep");

		service.deleteCollection(owner, "collection-delete");
		assertThat(collections.findById("collection-delete")).isEmpty();
		assertThat(records.findById("record-keep")).isPresent();
	}

	@Test
	void relationAddValidatesOwnerDiaryAndDuplicatesWhileRemovePreservesSource() {
		String ownerA = account();
		String ownerB = account();
		diary(ownerA, "diary-a");
		diary(ownerA, "diary-other");
		diary(ownerB, "diary-b");
		service.createRecord(ownerA, recordRequest("same-item", "diary-a"));
		service.createRecord(ownerA, recordRequest("cross-item", "diary-other"));
		service.createRecord(ownerB, recordRequest("foreign-item", "diary-b"));
		service.createCollection(ownerA, collectionRequest("collection-a", "diary-a", List.of()));
		service.createCollection(ownerB, collectionRequest("collection-b", "diary-b", List.of()));

		assertThat(service.addCollectionItem(ownerA, "collection-a", "same-item").itemIds())
			.containsExactly("same-item");
		assertConflict(() -> service.addCollectionItem(ownerA, "collection-a", "same-item"));
		assertConflict(() -> service.addCollectionItem(ownerA, "collection-a", "cross-item"));
		assertNotFound(() -> service.addCollectionItem(ownerA, "collection-a", "foreign-item"));
		assertNotFound(() -> service.addCollectionItem(ownerA, "collection-b", "same-item"));

		service.removeCollectionItem(ownerA, "collection-a", "same-item");
		assertThat(records.findById("same-item")).isPresent();
		assertThat(collectionItems.findByCollection_IdOrderByPosition("collection-a")).isEmpty();
	}

	@Test
	void collectionOrderRequiresExactDiarySetAndPersistsRequestedOrder() {
		String owner = account();
		diary(owner, "diary-a");
		service.createCollection(owner, collectionRequest("collection-a", "diary-a", List.of()));
		service.createCollection(owner, collectionRequest("collection-b", "diary-a", List.of()));

		var ordered = service.replaceCollectionOrder(owner, "diary-a",
			new ReplaceCollectionOrderRequest(List.of("collection-b", "collection-a")));

		assertThat(ordered).extracting(value -> value.id()).containsExactly("collection-b", "collection-a");
		assertThat(collections.findAllByDiary_IdAndOwner_IdOrderByPositionAsc("diary-a", owner))
			.extracting(value -> value.getId()).containsExactly("collection-b", "collection-a");
		assertConflict(() -> service.replaceCollectionOrder(owner, "diary-a",
			new ReplaceCollectionOrderRequest(List.of("collection-a"))));
	}

	@Test
	void wishlistConversionCreatesRecordReplacesMembershipAndRemovesWishlist() {
		String owner = account();
		diary(owner, "diary-a");
		service.createWishlist(owner, wishlistRequest("wish-1", "diary-a"));
		service.createCollection(owner, collectionRequest("collection-a", "diary-a", List.of("wish-1")));

		var result = service.convertWishlist(owner, "wish-1", convertRequest("record-from-wish"));

		assertThat(result.record().id()).isEqualTo("record-from-wish");
		assertThat(wishlist.findById("wish-1")).isEmpty();
		assertThat(result.collections()).singleElement().satisfies(value ->
			assertThat(value.itemIds()).containsExactly("record-from-wish"));
	}

	@Test
	void wishlistRejectsAlreadyVisitedPlace() {
		String owner = account();
		diary(owner, "diary-a");
		service.createRecord(owner, recordRequest("record-a", "diary-a"));
		assertConflict(() -> service.createWishlist(owner, wishlistRequestForPlace("wish-a", "diary-a", "place-id")));
	}

	@Test
	void directRecordCreationConsumesWishlistAndTransfersMembershipInSameDiaryOnly() {
		String owner = account();
		diary(owner, "diary-a");
		diary(owner, "diary-other");
		service.createWishlist(owner, wishlistRequestForPlace("wish-a", "diary-a", "place-id"));
		service.createWishlist(owner, wishlistRequestForPlace("wish-other", "diary-other", "place-id"));
		service.createCollection(owner, collectionRequest("collection-a", "diary-a", List.of("wish-a")));
		service.createRecord(owner, recordRequest("record-a", "diary-a"));
		assertThat(wishlist.findById("wish-a")).isEmpty();
		assertThat(globalItemIds.findById("wish-a")).isEmpty();
		assertThat(wishlist.findById("wish-other")).isPresent();
		assertThat(service.loadArchive(owner).collections().getFirst().itemIds()).containsExactly("record-a");
		assertThat(service.loadArchive(owner).revisitIntents()).isEmpty();
	}

	@Test
	void revisitRequiresVisitPreventsDuplicatesIsOwnerScopedAndCanBeDeleted() {
		String ownerA = account();
		String ownerB = account();
		diary(ownerA, "diary-a");
		diary(ownerB, "diary-b");
		assertConflict(() -> service.createRevisitIntent(ownerA, new CreateRevisitIntentRequest("revisit-a", "diary-a", "place-id")));
		service.createRecord(ownerA, recordRequest("record-a", "diary-a"));
		assertThat(service.createRevisitIntent(ownerA, new CreateRevisitIntentRequest("revisit-a", "diary-a", "place-id")).created()).isTrue();
		assertThat(service.createRevisitIntent(ownerA, new CreateRevisitIntentRequest("revisit-b", "diary-a", "place-id")).created()).isFalse();
		assertNotFound(() -> service.deleteRevisitIntent(ownerB, "revisit-a"));
		service.deleteRevisitIntent(ownerA, "revisit-a");
		assertThat(revisitIntents.findById("revisit-a")).isEmpty();
	}

	@Test
	void revisitSurvivesOneVisitDeletionAndIsRemovedWithLastVisit() {
		String owner = account();
		diary(owner, "diary-a");
		service.createRecord(owner, recordRequest("record-a", "diary-a"));
		service.createRecord(owner, recordRequest("record-b", "diary-a"));
		service.createRevisitIntent(owner, new CreateRevisitIntentRequest("revisit-a", "diary-a", "place-id"));
		service.deleteRecord(owner, "record-a");
		assertThat(revisitIntents.findById("revisit-a")).isPresent();
		service.deleteRecord(owner, "record-b");
		assertThat(revisitIntents.findById("revisit-a")).isEmpty();
	}

	@Test
	void concurrentWishlistAndRecordNeverLeaveVisitedWishlist() throws Exception {
		String owner = account();
		diary(owner, "diary-a");
		race(() -> service.createRecord(owner, recordRequest("record-a", "diary-a")), () -> {
			try { service.createWishlist(owner, wishlistRequestForPlace("wish-a", "diary-a", "place-id")); }
			catch (ApiException exception) { assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT); }
		});
		assertThat(records.findById("record-a")).isPresent();
		assertThat(wishlist.findById("wish-a")).isEmpty();
	}

	@Test
	void concurrentRevisitCreationAndLastRecordDeletionLeaveNoIntent() throws Exception {
		String owner = account();
		diary(owner, "diary-a");
		service.createRecord(owner, recordRequest("record-a", "diary-a"));
		race(() -> service.deleteRecord(owner, "record-a"), () -> {
			try { service.createRevisitIntent(owner, new CreateRevisitIntentRequest("revisit-a", "diary-a", "place-id")); }
			catch (ApiException exception) { assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT); }
		});
		assertThat(revisitIntents.count()).isZero();
	}

	@Test
	void concurrentDeletionOfTwoRemainingVisitsCleansRevisit() throws Exception {
		String owner = account();
		diary(owner, "diary-a");
		service.createRecord(owner, recordRequest("record-a", "diary-a"));
		service.createRecord(owner, recordRequest("record-b", "diary-a"));
		service.createRevisitIntent(owner, new CreateRevisitIntentRequest("revisit-a", "diary-a", "place-id"));
		race(() -> service.deleteRecord(owner, "record-a"), () -> service.deleteRecord(owner, "record-b"));
		assertThat(revisitIntents.count()).isZero();
	}

	private void race(Runnable first, Runnable second) throws Exception {
		var ready = new java.util.concurrent.CountDownLatch(2);
		var start = new java.util.concurrent.CountDownLatch(1);
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
			for (Runnable operation : List.of(first, second)) tasks.add(executor.submit(() -> {
				ready.countDown();
				try { if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start timeout"); }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new RuntimeException(exception); }
				operation.run();
			}));
			assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (var task : tasks) task.get(15, java.util.concurrent.TimeUnit.SECONDS);
		}
	}

	@Test
	void conversionRollsBackRecordAndEarlierMembershipWhenLaterMembershipConflicts() {
		String owner = account();
		diary(owner, "diary-a");
		service.createWishlist(owner, wishlistRequest("wish-rollback", "diary-a"));
		service.createCollection(owner, collectionRequest("a-collection", "diary-a", List.of("wish-rollback")));
		service.createCollection(owner, collectionRequest("z-collection", "diary-a", List.of("wish-rollback")));
		jdbc.update("insert into collection_items(collection_id,item_id,item_type,position) values (?,?,?,?)",
			"z-collection", "record-rollback", "record", 1);

		assertConflict(() -> service.convertWishlist(owner, "wish-rollback", convertRequest("record-rollback")));

		assertThat(records.findById("record-rollback")).isEmpty();
		assertThat(globalItemIds.findById("record-rollback")).isEmpty();
		assertThat(collectionItems.findById_CollectionIdAndId_ItemId("a-collection", "record-rollback")).isEmpty();
		assertThat(collectionItems.findById_CollectionIdAndId_ItemId("z-collection", "record-rollback")).isPresent();
	}

	@Test
	void bootstrapContainsCanonicalOwnerDataOnly() {
		String ownerA = account();
		String ownerB = account();
		diary(ownerA, "diary-a");
		diary(ownerB, "diary-b");
		service.createRecord(ownerA, recordRequest("record-a", "diary-a"));
		service.createWishlist(ownerA, wishlistRequest("wish-a", "diary-a"));
		service.createCollection(ownerA, collectionRequest("collection-a", "diary-a", List.of("record-a", "wish-a")));
		service.createRevisitIntent(ownerA, new CreateRevisitIntentRequest("revisit-a", "diary-a", "place-id"));
		service.createRecord(ownerB, recordRequest("record-b", "diary-b"));

		var response = service.loadArchive(ownerA);

		assertThat(response.apiVersion()).isEqualTo("v1");
		assertThat(response.serverTime()).isNotNull();
		assertThat(response.diaries()).extracting(value -> value.id()).containsExactly("diary-a");
		assertThat(response.records()).extracting(value -> value.id()).containsExactly("record-a");
		assertThat(response.wishlist()).extracting(value -> value.id()).containsExactly("wish-a");
		assertThat(response.collections()).singleElement().satisfies(value ->
			assertThat(value.itemIds()).containsExactly("record-a", "wish-a"));
		assertThat(response.revisitIntents()).extracting(value -> value.id()).containsExactly("revisit-a");
	}

	private String account() {
		return accounts.saveAndFlush(AccountEntity.create()).getId();
	}

	private void diary(String owner, String id) {
		service.createDiary(owner, new CreateDiaryRequest(id, id, DiaryTheme.NOTEBOOK));
	}

	private CreateRecordRequest recordRequest(String id, String diaryId) {
		return recordRequest(id, diaryId, "place-" + id);
	}

	private CreateRecordRequest recordRequest(String id, String diaryId, String placeName) {
		return new CreateRecordRequest(id, diaryId, "record", "place-id", placeName, "한식",
			"2026년 9월 8일", "memo", "서울", RecordVisibility.PRIVATE,
			Instant.parse("2026-09-08T03:00:00Z"), new BigDecimal("4.5"), "menu", 12000L, "note", "photo-ref");
	}

	private CreateWishlistRequest wishlistRequest(String id, String diaryId) {
		return wishlistRequestForPlace(id, diaryId, "wishlist-place-id");
	}

	private CreateWishlistRequest wishlistRequestForPlace(String id, String diaryId, String placeId) {
		return new CreateWishlistRequest(id, diaryId, "wishlist", placeId, "wishlist-place", "카페",
			"2026년 9월 8일", "memo", "서울", null, null, null, null, null);
	}

	private CreateCollectionRequest collectionRequest(String id, String diaryId, List<String> itemIds) {
		return new CreateCollectionRequest(id, diaryId, id, "memo", itemIds);
	}

	private ConvertWishlistRequest convertRequest(String id) {
		return new ConvertWishlistRequest(id, "converted-place", "converted", "카페", "2026년 9월 8일",
			"converted memo", "서울", RecordVisibility.PRIVATE, Instant.parse("2026-09-08T04:00:00Z"),
			new BigDecimal("4.0"), "menu", 9000L, null, null);
	}

	private void assertConflict(Runnable operation) {
		ApiException exception = assertThrows(ApiException.class, operation::run);
		assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(exception.code()).isEqualTo(ApiErrorCode.CONFLICT);
	}

	private void assertNotFound(Runnable operation) {
		ApiException exception = assertThrows(ApiException.class, operation::run);
		assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(exception.code()).isEqualTo(ApiErrorCode.NOT_FOUND);
	}
}
