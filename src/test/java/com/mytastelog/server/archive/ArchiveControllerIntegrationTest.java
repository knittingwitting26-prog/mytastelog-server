package com.mytastelog.server.archive;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.account.AuthenticatedAccount;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.collection.CollectionItemRepository;
import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordVisibility;
import com.mytastelog.server.wishlist.WishlistRepository;
import com.mytastelog.server.revisit.RevisitIntentRepository;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ArchiveControllerIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired ArchiveService service;
	@Autowired AccountRepository accounts;
	@Autowired DiaryRepository diaries;
	@Autowired RecordRepository records;
	@Autowired WishlistRepository wishlist;
	@Autowired CollectionRepository collections;
	@Autowired CollectionItemRepository collectionItems;
	@Autowired RevisitIntentRepository revisitIntents;

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
	void securityRequiresAuthenticationAndCsrfUsingContractErrors() throws Exception {
		String accountId = account();
		mvc.perform(get("/api/v1/archive"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

		mvc.perform(post("/api/v1/diaries").with(asAccount(accountId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"id\":\"diary-a\",\"name\":\"Diary\",\"theme\":\"notebook\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	void allCrudRoutesFollowJsonAndStatusContract() throws Exception {
		String accountId = account();
		RequestPostProcessor account = asAccount(accountId);

		mvc.perform(post("/api/v1/diaries").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"id\":\"diary-a\",\"name\":\"Diary\",\"theme\":\"notebook\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.ownerId").value(accountId))
			.andExpect(jsonPath("$.data.theme").value("notebook"));
		mvc.perform(post("/api/v1/diaries").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"id\":\"diary-a\",\"name\":\"Diary\",\"theme\":\"notebook\"}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/diaries").with(account))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value("diary-a"));
		mvc.perform(patch("/api/v1/diaries/diary-a").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Renamed\",\"theme\":\"travel\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.theme").value("travel"));

		mvc.perform(post("/api/v1/records").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(recordJson("record-a", "diary-a")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.type").value("record"))
			.andExpect(jsonPath("$.data.latitude").value(37.566))
			.andExpect(jsonPath("$.data.longitude").value(126.978))
			.andExpect(jsonPath("$.data.visitAt").value("2026-09-08T03:00:00Z"));
		mvc.perform(patch("/api/v1/records/record-a").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"placeName\":\"Updated\",\"visitAt\":\"2026-08-20T15:00:00Z\",\"rating\":null,\"price\":0}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.placeName").value("Updated"))
			.andExpect(jsonPath("$.data.visitAt").value("2026-08-20T15:00:00Z"))
			.andExpect(jsonPath("$.data.rating").doesNotExist());
		mvc.perform(get("/api/v1/archive").with(account))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.records[0].visitAt").value("2026-08-20T15:00:00Z"));

		mvc.perform(post("/api/v1/wishlist").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(wishlistJson("wish-a", "diary-a")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.type").value("wishlist"))
			.andExpect(jsonPath("$.data.latitude").value(37.566))
			.andExpect(jsonPath("$.data.longitude").value(126.978));
		mvc.perform(patch("/api/v1/wishlist/wish-a").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"placeName\":\"Wishlist updated\",\"memo\":\"new memo\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.placeName").value("Wishlist updated"));

		mvc.perform(post("/api/v1/collections").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"id\":\"collection-a\",\"diaryId\":\"diary-a\",\"name\":\"A\",\"memo\":\"memo\",\"itemIds\":[\"record-a\",\"wish-a\"]}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.itemIds[1]").value("wish-a"));
		mvc.perform(post("/api/v1/collections").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"id\":\"collection-b\",\"diaryId\":\"diary-a\",\"name\":\"B\",\"memo\":\"memo\",\"itemIds\":[]}"))
			.andExpect(status().isCreated());
		mvc.perform(patch("/api/v1/collections/collection-a").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"A updated\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("A updated"));

		mvc.perform(delete("/api/v1/collections/collection-a/items/record-a").with(account).with(csrf()))
			.andExpect(status().isNoContent()).andExpect(content().string(""));
		mvc.perform(post("/api/v1/collections/collection-a/items/record-a").with(account).with(csrf()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.itemIds[1]").value("record-a"));
		mvc.perform(put("/api/v1/diaries/diary-a/collection-order").with(account).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"collectionIds\":[\"collection-b\",\"collection-a\"]}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value("collection-b"));

		mvc.perform(post("/api/v1/wishlist/wish-a/convert-to-record").with(account).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(convertJson("record-converted")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.record.id").value("record-converted"))
			.andExpect(jsonPath("$.data.record.latitude").value(37.566))
			.andExpect(jsonPath("$.data.record.longitude").value(126.978))
			.andExpect(jsonPath("$.data.wishlist").doesNotExist())
			.andExpect(jsonPath("$.data.collections[0].itemIds[0]").value("record-converted"));

		mvc.perform(post("/api/v1/revisit").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"id\":\"revisit-a\",\"diaryId\":\"diary-a\",\"placeId\":\"converted\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.placeId").value("converted"));
		mvc.perform(get("/api/v1/archive").with(account)).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.revisitIntents[0].id").value("revisit-a"));
		mvc.perform(delete("/api/v1/revisit/revisit-a").with(account).with(csrf())).andExpect(status().isNoContent());

		mvc.perform(delete("/api/v1/records/record-a").with(account).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/v1/collections/collection-a").with(account).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/v1/collections/collection-b").with(account).with(csrf()))
			.andExpect(status().isNoContent());
		org.assertj.core.api.Assertions.assertThat(records.findById("record-converted")).isPresent();
	}

	@Test
	void archiveRecordsAreOrderedByVisitAtDescendingInsteadOfCreationTime() throws Exception {
		String accountId = account();
		RequestPostProcessor account = asAccount(accountId);
		service.createDiary(accountId, new CreateDiaryRequest("diary-a", "Diary", DiaryTheme.NOTEBOOK));

		mvc.perform(post("/api/v1/records").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(recordJson("created-first-visited-old", "diary-a")
				.replace("2026-09-08T03:00:00Z", "2026-08-20T00:00:00Z")))
			.andExpect(status().isCreated());
		mvc.perform(post("/api/v1/records").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(recordJson("created-last-visited-new", "diary-a")
				.replace("2026-09-08T03:00:00Z", "2026-09-10T00:00:00Z")))
			.andExpect(status().isCreated());

		mvc.perform(get("/api/v1/archive").with(account))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.records[0].id").value("created-last-visited-new"))
			.andExpect(jsonPath("$.data.records[1].id").value("created-first-visited-old"));
	}

	@Test
	void coordinatePairIsValidatedAndLegacyNullCoordinatesRemainCompatible() throws Exception {
		String accountId = account();
		RequestPostProcessor account = asAccount(accountId);
		service.createDiary(accountId, new CreateDiaryRequest("diary-a", "Diary", DiaryTheme.NOTEBOOK));

		mvc.perform(post("/api/v1/records").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(recordJson("partial-coordinate", "diary-a").replace(",\"longitude\":126.978", "")))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		mvc.perform(post("/api/v1/wishlist").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"id":"legacy","diaryId":"diary-a","type":"wishlist","placeId":"manual:legacy",
				"placeName":"Manual","category":"카페","date":"date","memo":"","address":"서울"}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.latitude").doesNotExist())
			.andExpect(jsonPath("$.data.longitude").doesNotExist());
		mvc.perform(get("/api/v1/archive").with(account))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.wishlist[0].latitude").doesNotExist());
	}

	@Test
	void bootstrapIsOwnerScopedAndForeignOrUnknownFieldsReturnContractErrors() throws Exception {
		String ownerA = account();
		String ownerB = account();
		service.createDiary(ownerA, new CreateDiaryRequest("diary-a", "A", DiaryTheme.NOTEBOOK));
		service.createDiary(ownerB, new CreateDiaryRequest("diary-b", "B", DiaryTheme.NOTEBOOK));
		service.createRecord(ownerB, new CreateRecordRequest("record-b", "diary-b", "record", "place",
			"B place", "category", "date", "memo", "address", RecordVisibility.PRIVATE,
			java.time.Instant.parse("2026-09-08T03:00:00Z"), null, null, null, null, null));

		mvc.perform(get("/api/v1/archive").with(asAccount(ownerA)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.apiVersion").value("v1"))
			.andExpect(jsonPath("$.data.diaries.length()").value(1))
			.andExpect(jsonPath("$.data.records.length()").value(0));
		mvc.perform(patch("/api/v1/records/record-b").with(asAccount(ownerA)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"attack\"}"))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		mvc.perform(post("/api/v1/diaries").with(asAccount(ownerA)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"id\":\"bad\",\"name\":\"Bad\",\"theme\":\"notebook\",\"ownerId\":\"forged\"}"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void wishlistCreateRejectsRecordOnlyUnknownFields() throws Exception {
		String accountId = account();
		RequestPostProcessor account = asAccount(accountId);
		service.createDiary(accountId, new CreateDiaryRequest("diary-a", "Diary", DiaryTheme.NOTEBOOK));

		mvc.perform(post("/api/v1/wishlist").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(wishlistWithRecordOnlyFieldJson("wish-visibility", "visibility", "\"private\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		mvc.perform(post("/api/v1/wishlist").with(account).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(wishlistWithRecordOnlyFieldJson("wish-visit-at", "visitAt", "\"2026-09-09T00:00:00Z\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	private String account() {
		return accounts.saveAndFlush(AccountEntity.create()).getId();
	}

	private RequestPostProcessor asAccount(String accountId) {
		var token = new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(accountId), "n/a",
			List.of(new SimpleGrantedAuthority("ROLE_USER")));
		return authentication(token);
	}

	private String recordJson(String id, String diaryId) {
		return """
			{"id":"%s","diaryId":"%s","type":"record","placeId":"place","placeName":"Place",
			"category":"한식","date":"2026년 9월 8일","memo":"memo","address":"서울",
			"latitude":37.566,"longitude":126.978,"visibility":"private","visitAt":"2026-09-08T03:00:00Z","rating":4.5,"price":12000}
			""".formatted(id, diaryId);
	}

	private String wishlistJson(String id, String diaryId) {
		return """
			{"id":"%s","diaryId":"%s","type":"wishlist","placeId":"wish-place","placeName":"Wish",
			"category":"카페","date":"2026년 9월 8일","memo":"memo","address":"서울","latitude":37.566,"longitude":126.978}
			""".formatted(id, diaryId);
	}

	private String wishlistWithRecordOnlyFieldJson(String id, String field, String value) {
		return """
			{"id":"%s","diaryId":"diary-a","type":"wishlist","placeId":"naver:8db086be9323cda8506bf597fd61c5ac",
			"placeName":"스타벅스 시청점","category":"카페,디저트>카페","date":"2026년 9월 9일",
			"memo":"테스트","address":"서울특별시 중구","menu":"테스트","price":4000,"rating":5,
			"%s":%s}
			""".formatted(id, field, value);
	}

	private String convertJson(String id) {
		return """
			{"id":"%s","placeId":"converted","placeName":"Converted","category":"카페",
			"date":"2026년 9월 8일","memo":"memo","address":"서울","visibility":"private",
			"visitAt":"2026-09-08T04:00:00Z"}
			""".formatted(id);
	}
}
