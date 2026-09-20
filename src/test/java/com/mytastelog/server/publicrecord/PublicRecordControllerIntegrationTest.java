package com.mytastelog.server.publicrecord;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.account.AuthenticatedAccount;
import com.mytastelog.server.archive.ArchiveService;
import com.mytastelog.server.archive.GlobalItemIdRepository;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.collection.CollectionItemRepository;
import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordVisibility;
import com.mytastelog.server.revisit.RevisitIntentRepository;
import com.mytastelog.server.wishlist.WishlistRepository;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PublicRecordControllerIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired ArchiveService archive;
	@Autowired AccountRepository accounts;
	@Autowired DiaryRepository diaries;
	@Autowired RecordRepository records;
	@Autowired WishlistRepository wishlist;
	@Autowired CollectionRepository collections;
	@Autowired CollectionItemRepository collectionItems;
	@Autowired RevisitIntentRepository revisitIntents;
	@Autowired GlobalItemIdRepository itemIds;
	String owner;

	@BeforeEach
	void setup() {
		collectionItems.deleteAll(); collections.deleteAll(); revisitIntents.deleteAll();
		records.deleteAll(); wishlist.deleteAll(); itemIds.deleteAll(); diaries.deleteAll(); accounts.deleteAll();
		owner = accounts.saveAndFlush(AccountEntity.create()).getId();
		archive.createDiary(owner, new CreateDiaryRequest("diary", "Diary", DiaryTheme.NOTEBOOK));
		create("private-record", "private-place", "Private", RecordVisibility.PRIVATE, new BigDecimal("5.0"));
		create("public-record", "public-place", "Public", RecordVisibility.PUBLIC, new BigDecimal("4.5"));
		create("public-record-2", "public-place", "Public", RecordVisibility.PUBLIC, new BigDecimal("3.5"));
	}

	@Test
	void anonymousListIsBoundedGroupedAndContainsOnlyPublicAllowlist() throws Exception {
		mvc.perform(get("/api/v1/public/records").param("limit", "100"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].placeId").value("public-place"))
			.andExpect(jsonPath("$.data[0].publicRecordCount").value(2))
			.andExpect(jsonPath("$.data[0].averageRating").value(4.0))
			.andExpect(jsonPath("$.data[0].price").doesNotExist())
			.andExpect(jsonPath("$.data[0].memo").doesNotExist())
			.andExpect(jsonPath("$.data[0].visitAt").doesNotExist())
			.andExpect(jsonPath("$.data[0].ownerId").doesNotExist())
			.andExpect(jsonPath("$.data[0].menus").doesNotExist());
		mvc.perform(get("/api/v1/public/records").param("north", "38").param("south", "37")
			.param("east", "127").param("west", "126"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
		mvc.perform(get("/api/v1/public/records").param("north", "38"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void detailNeverLeaksPrivateFieldsAndPrivateTransitionsTakeEffectImmediately() throws Exception {
		mvc.perform(get("/api/v1/public/records/private-record")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/public/records/public-record"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.placeName").value("Public"))
			.andExpect(jsonPath("$.data.address").value("서울"))
			.andExpect(jsonPath("$.data.rating").value(4.5))
			.andExpect(jsonPath("$.data.menu").value("menu"))
			.andExpect(jsonPath("$.data.price").doesNotExist())
			.andExpect(jsonPath("$.data.memo").doesNotExist()).andExpect(jsonPath("$.data.visitAt").doesNotExist())
			.andExpect(jsonPath("$.data.ownerId").doesNotExist()).andExpect(jsonPath("$.data.diaryId").doesNotExist())
			.andExpect(jsonPath("$.data.menus").doesNotExist());
		var principal = new AuthenticatedAccount(owner);
		var auth = new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
		mvc.perform(get("/api/v1/archive").with(authentication(auth)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.records[1].id").value("public-record"))
			.andExpect(jsonPath("$.data.records[1].price").value(12000));
		mvc.perform(patch("/api/v1/records/public-record").with(authentication(auth)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"private\"}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/public/records/public-record")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/public/records"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].publicRecordCount").value(1));
	}

	private void create(String id, String placeId, String placeName, RecordVisibility visibility, BigDecimal rating) {
		archive.createRecord(owner, new CreateRecordRequest(id, "diary", "record", placeId, placeName,
			"한식", "date", "secret memo", "서울", new BigDecimal("37.566"), new BigDecimal("126.978"),
			visibility, Instant.parse("2026-09-08T03:00:00Z"), rating, "menu", 12000L, "secret note", null));
	}
}
