package com.mytastelog.server.photo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PhotoControllerIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired ArchiveService archive;
	@Autowired AccountRepository accounts;
	@Autowired ObjectMapper objectMapper;

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
		MockHttpSession ownerSession = authenticatedSession(owner);
		CsrfExchange ownerCsrf = csrf(ownerSession);
		mvc.perform(multipart(url).file(jpeg()).session(ownerSession))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		mvc.perform(multipart(url).file(jpeg()).session(ownerSession)
				.header(ownerCsrf.headerName(), "wrong-token"))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		MockHttpSession anonymousSession = new MockHttpSession();
		CsrfExchange anonymousCsrf = csrf(anonymousSession);
		mvc.perform(multipart(url).file(jpeg()).session(anonymousSession)
				.header(anonymousCsrf.headerName(), anonymousCsrf.token()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

		MockHttpSession otherSession = authenticatedSession(other);
		CsrfExchange otherCsrf = csrf(otherSession);
		mvc.perform(multipart(url).file(jpeg()).session(otherSession)
				.header(otherCsrf.headerName(), otherCsrf.token()))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		mvc.perform(get(url).session(otherSession))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		mvc.perform(delete(url).session(otherSession)
				.header(otherCsrf.headerName(), otherCsrf.token()))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	void publicPhotoRequiresPublicVisibilityAndClosesImmediatelyWhenMadePrivate() throws Exception {
		String ownerUrl = "/api/v1/records/" + recordId + "/photo";
		String publicUrl = "/api/v1/public/records/" + recordId + "/photo";
		MockHttpSession session = authenticatedSession(owner);
		CsrfExchange token = csrf(session);
		mvc.perform(multipart(ownerUrl).file(jpeg()).session(session).header(token.headerName(), token.token()))
			.andExpect(status().isOk());
		mvc.perform(get(publicUrl)).andExpect(status().isNotFound());
		mvc.perform(patch("/api/v1/records/" + recordId).session(session).header(token.headerName(), token.token())
			.contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"public\"}"))
			.andExpect(status().isOk());
		mvc.perform(get(publicUrl)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
		mvc.perform(patch("/api/v1/records/" + recordId).session(session).header(token.headerName(), token.token())
			.contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"private\"}"))
			.andExpect(status().isOk());
		mvc.perform(get(publicUrl)).andExpect(status().isNotFound());
	}

	private void assertRoundTrip(String url) throws Exception {
		MockHttpSession session = authenticatedSession(owner);
		CsrfExchange csrf = csrf(session);
		mvc.perform(multipart(url).file(jpeg()).session(session).header(csrf.headerName(), csrf.token()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.hasPhoto").value(true))
			.andExpect(jsonPath("$.data.url").value(url));
		mvc.perform(get(url).session(session)).andExpect(status().isOk())
			.andExpect(content().contentType("image/jpeg"))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(content().bytes(jpeg().getBytes()));
		mvc.perform(delete(url).session(session).header(csrf.headerName(), csrf.token()))
			.andExpect(status().isNoContent());
		mvc.perform(get(url).session(session)).andExpect(status().isNotFound());
	}

	private MockMultipartFile jpeg() {
		return new MockMultipartFile("file", "ignored.jpg", "image/jpeg",
			new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
	}

	private MockHttpSession authenticatedSession(String accountId) {
		AuthenticatedAccount principal = new AuthenticatedAccount(accountId);
		var authentication = new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
			new SecurityContextImpl(authentication));
		return session;
	}

	private CsrfExchange csrf(MockHttpSession session) throws Exception {
		MvcResult result = mvc.perform(get("/api/v1/auth/csrf").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
			.andReturn();
		JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
		return new CsrfExchange(data.get("headerName").asText(), data.get("token").asText());
	}

	private record CsrfExchange(String headerName, String token) {}

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
