package com.mytastelog.server.photo;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.account.AuthenticatedAccount;
import com.mytastelog.server.archive.ArchiveService;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateWishlistRequest;
import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.record.RecordVisibility;

@SpringBootTest(properties = {"naver.local.client-id=test", "naver.local.client-secret=test"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PhotoControllerIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired ArchiveService archive;
	@Autowired AccountRepository accounts;

	String owner;
	String other;
	String recordId;
	String wishlistId;
	String collectionId;

	@BeforeEach
	void setup() {
		String suffix = UUID.randomUUID().toString();
		String diaryId = "photo-diary-" + suffix;
		recordId = "photo-record-" + suffix;
		wishlistId = "photo-wish-" + suffix;
		collectionId = "photo-collection-" + suffix;
		owner = accounts.saveAndFlush(AccountEntity.create()).getId();
		other = accounts.saveAndFlush(AccountEntity.create()).getId();
		archive.createDiary(owner, new CreateDiaryRequest(diaryId, "Diary", DiaryTheme.NOTEBOOK));
		archive.createRecord(owner, new CreateRecordRequest(recordId, diaryId, "record", "record-place",
			"Record", "food", "date", "memo", "address", RecordVisibility.PRIVATE,
			Instant.parse("2026-09-08T03:00:00Z"), null, null, null, null, null));
		archive.createWishlist(owner, new CreateWishlistRequest(wishlistId, diaryId, "wishlist", "wish-place",
			"Wish", "cafe", "date", "memo", "address", null, null, null, null, null));
		archive.createCollection(owner, new CreateCollectionRequest(collectionId, diaryId, "Collection",
			"memo", List.of()));
	}

	@Test
	void recordWishlistAndCollectionEndpointsUploadStreamAndDelete() throws Exception {
		assertRoundTrip("/api/v1/records/" + recordId + "/photo");
		assertRoundTrip("/api/v1/wishlist/" + wishlistId + "/photo");
		assertRoundTrip("/api/v1/collections/" + collectionId + "/photo");
	}

	@Test
	void endpointRequiresAuthenticationAndRejectsAnotherOwnerForEveryOperation() throws Exception {
		String url = "/api/v1/records/" + recordId + "/photo";
		mvc.perform(get(url)).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
		mvc.perform(multipart(url).file(jpeg()).with(asAccount(other)).with(csrf()))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		mvc.perform(get(url).with(asAccount(other)))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		mvc.perform(delete(url).with(asAccount(other)).with(csrf()))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	private void assertRoundTrip(String url) throws Exception {
		mvc.perform(multipart(url).file(jpeg()).with(asAccount(owner)).with(csrf()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.hasPhoto").value(true))
			.andExpect(jsonPath("$.data.url").value(url));
		mvc.perform(get(url).with(asAccount(owner))).andExpect(status().isOk())
			.andExpect(content().contentType("image/jpeg"))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(content().bytes(jpeg().getBytes()));
		mvc.perform(delete(url).with(asAccount(owner)).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get(url).with(asAccount(owner))).andExpect(status().isNotFound());
	}

	private MockMultipartFile jpeg() {
		return new MockMultipartFile("file", "ignored.jpg", "image/jpeg",
			new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
	}

	private RequestPostProcessor asAccount(String accountId) {
		return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(accountId), "n/a",
			new AuthenticatedAccount(accountId).getAuthorities()));
	}

	@TestConfiguration
	static class Config {
		@Bean @Primary PhotoStorage testPhotoStorage() { return new MemoryPhotoStorage(); }
	}

	static class MemoryPhotoStorage implements PhotoStorage {
		private final Map<String, PhotoContent> content = new HashMap<>();
		@Override public void assertConfigured() {}
		@Override public String store(PhotoEntityType type, PhotoContent value) {
			String key = "photos/" + type.path() + "/" + UUID.randomUUID() + ".jpg";
			content.put(key, value); return key;
		}
		@Override public PhotoContent read(String key) { return content.get(key); }
		@Override public void delete(String key) { content.remove(key); }
		@Override public boolean isManagedReference(String value) {
			return value != null && value.matches("photos/(records|wishlist|collections)/[0-9a-f-]{36}\\.jpg");
		}
	}
}
