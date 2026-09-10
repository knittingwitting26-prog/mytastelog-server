package com.mytastelog.server.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.archive.ArchiveService;
import com.mytastelog.server.archive.GlobalItemIdRepository;
import com.mytastelog.server.archive.dto.ArchiveRequests.ConvertWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateWishlistRequest;
import com.mytastelog.server.collection.CollectionItemRepository;
import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordVisibility;
import com.mytastelog.server.revisit.RevisitIntentRepository;
import com.mytastelog.server.wishlist.WishlistRepository;

@SpringBootTest(properties = {"naver.local.client-id=test", "naver.local.client-secret=test"})
@ActiveProfiles("test")
class PhotoServiceIntegrationTest {
	@Autowired PhotoService photos;
	@Autowired ArchiveService archive;
	@Autowired FakePhotoStorage storage;
	@Autowired AccountRepository accounts;
	@Autowired DiaryRepository diaries;
	@Autowired RecordRepository records;
	@Autowired WishlistRepository wishlist;
	@Autowired CollectionRepository collections;
	@Autowired CollectionItemRepository collectionItems;
	@Autowired RevisitIntentRepository revisits;
	@Autowired GlobalItemIdRepository globalIds;

	@BeforeEach
	void clean() {
		collectionItems.deleteAll();
		collections.deleteAll();
		revisits.deleteAll();
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

	private String setup(String recordId, String wishlistId, String collectionId) {
		String owner = accounts.saveAndFlush(AccountEntity.create()).getId();
		archive.createDiary(owner, new CreateDiaryRequest("diary-a", "Diary", DiaryTheme.NOTEBOOK));
		archive.createRecord(owner, new CreateRecordRequest(recordId, "diary-a", "record", "place-r", "Record",
			"food", "date", "memo", "address", RecordVisibility.PRIVATE,
			Instant.parse("2026-09-08T03:00:00Z"), null, null, null, null, null));
		archive.createWishlist(owner, new CreateWishlistRequest(wishlistId, "diary-a", "wishlist", "place-w",
			"Wish", "cafe", "date", "memo", "address", null, null, null, null, null));
		archive.createCollection(owner, new CreateCollectionRequest(collectionId, "diary-a", "Collection", "memo",
			java.util.List.of()));
		return owner;
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

	private void assertForbidden(Runnable operation) {
		assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.code()).isEqualTo(ApiErrorCode.FORBIDDEN);
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
			return reference != null && reference.matches("photos/(records|wishlist|collections)/[0-9a-f-]{36}\\.(jpg|png|webp)");
		}
		void reset() { objects.clear(); deleted.clear(); failStore = false; failDelete = false; }
	}
}
