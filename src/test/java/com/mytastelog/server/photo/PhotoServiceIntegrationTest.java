package com.mytastelog.server.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.archive.ArchiveService;
import com.mytastelog.server.archive.GlobalItemIdRepository;
import com.mytastelog.server.archive.dto.ArchiveRequests.ConvertWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.RecordMenuRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.UpdateRecordRequest;
import com.mytastelog.server.collection.CollectionItemRepository;
import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordMenuRepository;
import com.mytastelog.server.record.RecordVisibility;
import com.mytastelog.server.revisit.RevisitIntentRepository;
import com.mytastelog.server.wishlist.WishlistRepository;

@SpringBootTest
@ActiveProfiles("test")
class PhotoServiceIntegrationTest {
	@Autowired PhotoService photos;
	@Autowired ArchiveService archive;
	@Autowired FakePhotoStorage storage;
	@Autowired AccountRepository accounts;
	@Autowired DiaryRepository diaries;
	@Autowired RecordRepository records;
	@Autowired RecordMenuRepository recordMenus;
	@Autowired WishlistRepository wishlist;
	@Autowired CollectionRepository collections;
	@Autowired CollectionItemRepository collectionItems;
	@Autowired RevisitIntentRepository revisits;
	@Autowired GlobalItemIdRepository globalIds;
	@Autowired PlatformTransactionManager transactionManager;

	@BeforeEach
	void clean() {
		collectionItems.deleteAll();
		collections.deleteAll();
		revisits.deleteAll();
		recordMenus.deleteAll();
		records.deleteAll();
		wishlist.deleteAll();
		globalIds.deleteAll();
		diaries.deleteAll();
		accounts.deleteAll();
		storage.reset();
	}

	@Test
	void recordUploadReadReplaceRemoveAndEntityDeleteCleanup() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String first = uploadRecord(owner);
		assertThat(photos.readRecord(owner, "record-a").bytes()).containsExactly(0xff, 0xd8, 0xff, 1);

		photos.uploadRecord(owner, "record-a", jpeg(2));
		String second = records.findById("record-a").orElseThrow().getPhotoReference();
		assertThat(second).isNotEqualTo(first);
		assertThat(storage.deleted).contains(first);

		photos.deleteRecordPhoto(owner, "record-a");
		assertThat(records.findById("record-a").orElseThrow().getPhotoReference()).isNull();
		assertThat(storage.deleted).contains(second);

		String third = uploadRecord(owner);
		archive.deleteRecord(owner, "record-a");
		assertThat(storage.deleted).contains(third);
	}

	@Test
	void wishlistAndCollectionSupportOnePhotoAndEntityDeleteCleanup() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String wishFirst = uploadWishlist(owner);
		photos.uploadWishlist(owner, "wish-a", jpeg(3));
		assertThat(storage.deleted).contains(wishFirst);
		String wishSecond = wishlist.findById("wish-a").orElseThrow().getPhotoReference();
		photos.deleteWishlistPhoto(owner, "wish-a");
		assertThat(storage.deleted).contains(wishSecond);
		String wishThird = uploadWishlist(owner);
		archive.deleteWishlist(owner, "wish-a");
		assertThat(storage.deleted).contains(wishThird);

		String collectionFirst = uploadCollection(owner);
		photos.uploadCollection(owner, "collection-a", jpeg(4));
		assertThat(storage.deleted).contains(collectionFirst);
		String collectionSecond = collections.findById("collection-a").orElseThrow().getPhotoReference();
		photos.deleteCollectionPhoto(owner, "collection-a");
		assertThat(storage.deleted).contains(collectionSecond);
		String collectionThird = uploadCollection(owner);
		archive.deleteCollection(owner, "collection-a");
		assertThat(storage.deleted).contains(collectionThird);
	}

	@Test
	void wishlistConversionTransfersManagedObjectWithoutDeletingIt() {
		String owner = setup("record-a", "wish-a", "collection-a");
		archive.deleteRecord(owner, "record-a");
		String key = uploadWishlist(owner);

		archive.convertWishlist(owner, "wish-a", new ConvertWishlistRequest("record-from-wish", "place-w",
			"Converted", "cafe", "date", "memo", "address", RecordVisibility.PRIVATE,
			Instant.parse("2026-09-08T03:00:00Z"), BigDecimal.ONE, null, null, null, "local-photo:ignored"));

		assertThat(records.findById("record-from-wish").orElseThrow().getPhotoReference()).isEqualTo(key);
		assertThat(storage.deleted).doesNotContain(key);
		assertThat(photos.readRecord(owner, "record-from-wish").bytes()).isNotEmpty();
	}

	@Test
	void ownershipIsIsolatedForReadUploadAndDelete() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String other = accounts.saveAndFlush(AccountEntity.create()).getId();
		uploadRecord(owner);
		assertForbidden(() -> photos.readRecord(other, "record-a"));
		assertForbidden(() -> photos.uploadRecord(other, "record-a", jpeg(1)));
		assertForbidden(() -> photos.deleteRecordPhoto(other, "record-a"));
	}

	@Test
	void uploadFailurePreservesOldReferenceAndOldCleanupFailurePreservesNewReference() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String old = uploadRecord(owner);
		storage.failStore = true;
		assertThatThrownBy(() -> photos.uploadRecord(owner, "record-a", jpeg(2)))
			.isInstanceOf(ApiException.class);
		assertThat(records.findById("record-a").orElseThrow().getPhotoReference()).isEqualTo(old);

		storage.failStore = false;
		storage.failDelete = true;
		photos.uploadRecord(owner, "record-a", jpeg(3));
		assertThat(records.findById("record-a").orElseThrow().getPhotoReference()).isNotEqualTo(old);
	}

	@Test
	void recordMenuUploadReadReplaceAndDeleteUseDedicatedManagedKeys() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String menuId = menuId("record-a");

		photos.uploadRecordMenu(owner, "record-a", menuId, jpeg(1));
		String first = recordMenus.findById(menuId).orElseThrow().getPhotoReference();
		assertThat(first).startsWith("photos/record-menus/");
		assertThat(photos.readRecordMenu(owner, "record-a", menuId).bytes())
			.containsExactly(0xff, 0xd8, 0xff, 1);

		photos.uploadRecordMenu(owner, "record-a", menuId, jpeg(2));
		String second = recordMenus.findById(menuId).orElseThrow().getPhotoReference();
		assertThat(second).isNotEqualTo(first);
		assertThat(storage.deleted).contains(first);

		photos.deleteRecordMenuPhoto(owner, "record-a", menuId);
		assertThat(recordMenus.findById(menuId).orElseThrow().getPhotoReference()).isNull();
		assertThat(storage.deleted).contains(second);
	}

	@Test
	void recordMenuOwnershipChecksParentBeforeMenuMembership() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String other = accounts.saveAndFlush(AccountEntity.create()).getId();
		archive.createRecord(owner, recordRequest("record-b", "menu-b"));

		assertForbidden(() -> photos.uploadRecordMenu(other, "record-a", menuId("record-a"), jpeg(1)));
		assertForbidden(() -> photos.readRecordMenu(other, "record-a", menuId("record-a")));
		assertForbidden(() -> photos.deleteRecordMenuPhoto(other, "record-a", menuId("record-a")));
		assertNotFound(() -> photos.uploadRecordMenu(owner, "record-a", "menu-b", jpeg(1)));
		assertNotFound(() -> photos.readRecordMenu(owner, "missing-record", menuId("record-a")));
		assertNotFound(() -> photos.deleteRecordMenuPhoto(owner, "record-a", "missing-menu"));
	}

	@Test
	void recordMenuUploadUsesSharedJpegPngWebpAndValidationPolicy() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String menuId = menuId("record-a");
		photos.uploadRecordMenu(owner, "record-a", menuId, jpeg(1));
		photos.uploadRecordMenu(owner, "record-a", menuId,
			file("image/png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}));
		photos.uploadRecordMenu(owner, "record-a", menuId,
			file("image/webp", "RIFF1234WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));

		assertThatThrownBy(() -> photos.uploadRecordMenu(owner, "record-a", menuId,
			file("image/gif", "GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII))))
			.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> photos.uploadRecordMenu(owner, "record-a", menuId,
			file("image/jpeg", "not jpeg".getBytes(java.nio.charset.StandardCharsets.US_ASCII))))
			.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> photos.uploadRecordMenu(owner, "record-a", menuId,
			file("image/jpeg", oversizedJpeg())))
			.isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.status().value()).isEqualTo(413));
	}

	@Test
	void menuMetadataReplacementPreservesRetainedPhotoAndCleansRemovedPhotos() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String retainedId = menuId("record-a");
		photos.uploadRecordMenu(owner, "record-a", retainedId, jpeg(1));
		String retainedPhoto = recordMenus.findById(retainedId).orElseThrow().getPhotoReference();

		UpdateRecordRequest rename = new UpdateRecordRequest();
		rename.setMenus(List.of(new RecordMenuRequest(retainedId, "Renamed", 2000L, 0)));
		var renamed = archive.updateRecord(owner, "record-a", rename);
		assertThat(recordMenus.findById(retainedId).orElseThrow().getPhotoReference()).isEqualTo(retainedPhoto);
		assertThat(renamed.menus().getFirst().photoUrl())
			.isEqualTo("/api/v1/records/record-a/menus/" + retainedId + "/photo");

		UpdateRecordRequest replace = new UpdateRecordRequest();
		replace.setMenus(List.of(new RecordMenuRequest("replacement-menu", "New", 3000L, 0)));
		archive.updateRecord(owner, "record-a", replace);
		assertThat(recordMenus.findById(retainedId)).isEmpty();
		assertThat(recordMenus.findById("replacement-menu").orElseThrow().getPhotoReference()).isNull();
		assertThat(storage.deleted).contains(retainedPhoto);
	}

	@Test
	void recordMenuUploadRollbackCleansNewObjectAndPreservesOldReference() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String menuId = menuId("record-a");
		photos.uploadRecordMenu(owner, "record-a", menuId, jpeg(1));
		String oldReference = recordMenus.findById(menuId).orElseThrow().getPhotoReference();
		storage.deleted.clear();
		AtomicReference<String> newReference = new AtomicReference<>();

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			photos.uploadRecordMenu(owner, "record-a", menuId, jpeg(2));
			newReference.set(recordMenus.findById(menuId).orElseThrow().getPhotoReference());
			status.setRollbackOnly();
		});

		assertThat(recordMenus.findById(menuId).orElseThrow().getPhotoReference()).isEqualTo(oldReference);
		assertThat(storage.deleted).contains(newReference.get()).doesNotContain(oldReference);
	}

	@Test
	void menuMetadataRollbackKeepsRowAndPhotoObject() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String menuId = menuId("record-a");
		photos.uploadRecordMenu(owner, "record-a", menuId, jpeg(1));
		String reference = recordMenus.findById(menuId).orElseThrow().getPhotoReference();
		storage.deleted.clear();

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			UpdateRecordRequest clear = new UpdateRecordRequest();
			clear.setMenus(List.of());
			archive.updateRecord(owner, "record-a", clear);
			status.setRollbackOnly();
		});

		assertThat(recordMenus.findById(menuId).orElseThrow().getPhotoReference()).isEqualTo(reference);
		assertThat(storage.deleted).doesNotContain(reference);
	}

	@Test
	void recordMenuDeleteFailureLeavesDatabaseCleared() {
		String owner = setup("record-a", "wish-a", "collection-a");
		String menuId = menuId("record-a");
		photos.uploadRecordMenu(owner, "record-a", menuId, jpeg(1));
		String reference = recordMenus.findById(menuId).orElseThrow().getPhotoReference();
		storage.failDelete = true;

		photos.deleteRecordMenuPhoto(owner, "record-a", menuId);

		assertThat(recordMenus.findById(menuId).orElseThrow().getPhotoReference()).isNull();
		assertThat(storage.deleted).contains(reference);
	}

	@Test
	void emptyMenusAndRecordDeleteCleanEveryMenuPhotoAndContinueAfterDeleteFailures() {
		String owner = setup("record-a", "wish-a", "collection-a");
		UpdateRecordRequest addSecond = new UpdateRecordRequest();
		addSecond.setMenus(List.of(
			new RecordMenuRequest(menuId("record-a"), "First", 1000L, 0),
			new RecordMenuRequest("menu-second", "Second", 2000L, 1)));
		archive.updateRecord(owner, "record-a", addSecond);
		photos.uploadRecordMenu(owner, "record-a", menuId("record-a"), jpeg(1));
		photos.uploadRecordMenu(owner, "record-a", "menu-second", jpeg(2));
		List<String> menuPhotos = recordMenus.findByRecord_IdOrderByPositionAsc("record-a").stream()
			.map(value -> value.getPhotoReference()).toList();

		UpdateRecordRequest clear = new UpdateRecordRequest();
		clear.setMenus(List.of());
		archive.updateRecord(owner, "record-a", clear);
		assertThat(recordMenus.findByRecord_IdOrderByPositionAsc("record-a")).isEmpty();
		assertThat(storage.deleted).containsAll(menuPhotos);

		UpdateRecordRequest restore = new UpdateRecordRequest();
		restore.setMenus(List.of(
			new RecordMenuRequest("delete-a", "A", null, 0),
			new RecordMenuRequest("delete-b", "B", null, 1)));
		archive.updateRecord(owner, "record-a", restore);
		String representative = uploadRecord(owner);
		photos.uploadRecordMenu(owner, "record-a", "delete-a", jpeg(3));
		photos.uploadRecordMenu(owner, "record-a", "delete-b", jpeg(4));
		List<String> deleteMenuPhotos = recordMenus.findByRecord_IdOrderByPositionAsc("record-a").stream()
			.map(value -> value.getPhotoReference()).toList();
		storage.deleted.clear();
		storage.failDelete = true;

		archive.deleteRecord(owner, "record-a");

		assertThat(records.findById("record-a")).isEmpty();
		assertThat(recordMenus.findByRecord_IdOrderByPositionAsc("record-a")).isEmpty();
		assertThat(storage.deleted).contains(representative).containsAll(deleteMenuPhotos);
	}

	private String setup(String recordId, String wishlistId, String collectionId) {
		String owner = accounts.saveAndFlush(AccountEntity.create()).getId();
		archive.createDiary(owner, new CreateDiaryRequest("diary-a", "Diary", DiaryTheme.NOTEBOOK));
		archive.createRecord(owner, recordRequest(recordId, menuId(recordId)));
		archive.createWishlist(owner, new CreateWishlistRequest(wishlistId, "diary-a", "wishlist", "place-w",
			"Wish", "cafe", "date", "memo", "address", null, null, null, null, null));
		archive.createCollection(owner, new CreateCollectionRequest(collectionId, "diary-a", "Collection", "memo",
			java.util.List.of()));
		return owner;
	}

	private CreateRecordRequest recordRequest(String recordId, String menuId) {
		return new CreateRecordRequest(recordId, "diary-a", "record", "place-" + recordId, "Record",
			"food", "date", "memo", "address", null, null, RecordVisibility.PRIVATE,
			Instant.parse("2026-09-08T03:00:00Z"), null, null, null, null, null,
			List.of(new RecordMenuRequest(menuId, "Menu", 1000L, 0)));
	}

	private String menuId(String recordId) {
		return "menu-" + recordId;
	}

	private String uploadRecord(String owner) {
		photos.uploadRecord(owner, "record-a", jpeg(1));
		return records.findById("record-a").orElseThrow().getPhotoReference();
	}

	private String uploadWishlist(String owner) {
		photos.uploadWishlist(owner, "wish-a", jpeg(1));
		return wishlist.findById("wish-a").orElseThrow().getPhotoReference();
	}

	private String uploadCollection(String owner) {
		photos.uploadCollection(owner, "collection-a", jpeg(1));
		return collections.findById("collection-a").orElseThrow().getPhotoReference();
	}

	private MockMultipartFile jpeg(int value) {
		return new MockMultipartFile("file", "unsafe-name.exe", "image/jpeg",
			new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) value});
	}

	private MockMultipartFile file(String contentType, byte[] bytes) {
		return new MockMultipartFile("file", "unsafe-name.exe", contentType, bytes);
	}

	private byte[] oversizedJpeg() {
		byte[] bytes = new byte[(int) PhotoFileValidator.MAX_BYTES + 1];
		bytes[0] = (byte) 0xff;
		bytes[1] = (byte) 0xd8;
		bytes[2] = (byte) 0xff;
		return bytes;
	}

	private void assertForbidden(Runnable operation) {
		assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.code()).isEqualTo(ApiErrorCode.FORBIDDEN);
		});
	}

	private void assertNotFound(Runnable operation) {
		assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.code()).isEqualTo(ApiErrorCode.NOT_FOUND);
		});
	}

	@TestConfiguration
	static class StorageConfiguration {
		@Bean
		@Primary
		FakePhotoStorage fakePhotoStorage() {
			return new FakePhotoStorage();
		}
	}

	static class FakePhotoStorage implements PhotoStorage {
		final Map<String, PhotoContent> objects = new HashMap<>();
		final java.util.List<String> deleted = new java.util.ArrayList<>();
		boolean failStore;
		boolean failDelete;

		@Override public void assertConfigured() {}
		@Override public String store(PhotoEntityType type, PhotoContent content) {
			if (failStore) throw new PhotoStorageException(PhotoStorageException.Operation.UPLOAD, "upload failed");
			String key = "photos/" + type.path() + "/" + UUID.randomUUID() + ".jpg";
			objects.put(key, content);
			return key;
		}
		@Override public PhotoContent read(String key) {
			PhotoContent content = objects.get(key);
			if (content == null) throw new PhotoStorageException(PhotoStorageException.Operation.READ, "missing");
			return content;
		}
		@Override public void delete(String key) {
			deleted.add(key);
			if (failDelete) throw new PhotoStorageException(PhotoStorageException.Operation.DELETE, "delete failed");
			objects.remove(key);
		}
		@Override public boolean isManagedReference(String reference) {
			return reference != null && reference.matches("photos/(records|record-menus|wishlist|collections)/[0-9a-f-]{36}\\.(jpg|png|webp)");
		}
		void reset() { objects.clear(); deleted.clear(); failStore = false; failDelete = false; }
	}
}
