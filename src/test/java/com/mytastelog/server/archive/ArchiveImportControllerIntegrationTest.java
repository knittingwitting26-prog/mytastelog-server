package com.mytastelog.server.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.mytastelog.server.collection.CollectionItemRepository;
import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordMenuRepository;
import com.mytastelog.server.revisit.RevisitIntentRepository;
import com.mytastelog.server.wishlist.WishlistRepository;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ArchiveImportControllerIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired AccountRepository accounts;
	@Autowired DiaryRepository diaries;
	@Autowired RecordRepository records;
	@Autowired RecordMenuRepository recordMenus;
	@Autowired WishlistRepository wishlist;
	@Autowired CollectionRepository collections;
	@Autowired CollectionItemRepository relations;
	@Autowired RevisitIntentRepository revisits;
	@Autowired ArchiveImportItemRepository imports;

	@BeforeEach
	void cleanDatabase() {
		imports.deleteAll();
		relations.deleteAll();
		collections.deleteAll();
		revisits.deleteAll();
		recordMenus.deleteAll();
		records.deleteAll();
		wishlist.deleteAll();
		diaries.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void importRequiresAuthenticationAndCsrf() throws Exception {
		mvc.perform(post("/api/v1/archive/imports").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(manifest("transfer-a")))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
		String accountId = account();
		mvc.perform(post("/api/v1/archive/imports").with(asAccount(accountId)).contentType(MediaType.APPLICATION_JSON).content(manifest("transfer-a")))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	void explicitImportMapsIdsPreservesRelationsAndIsIdempotentPerOwner() throws Exception {
		String ownerA = account();
		String ownerB = account();
		RequestPostProcessor accountA = asAccount(ownerA);

		mvc.perform(post("/api/v1/archive/imports").with(accountA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(manifest("transfer-a")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("COMPLETE"))
			.andExpect(jsonPath("$.data.cleanupAllowed").value(true))
			.andExpect(jsonPath("$.data.items[0].entity").value("diary"))
			.andExpect(jsonPath("$.data.items[0].serverId").isNotEmpty())
			.andExpect(jsonPath("$.data.items[1].entity").value("record"))
			.andExpect(jsonPath("$.data.items[1].serverId").isNotEmpty())
			.andExpect(jsonPath("$.data.items[2].status").value("SKIPPED_BY_DOMAIN_RULE"))
			.andExpect(jsonPath("$.data.items[2].reason").value("RECORD_ALREADY_EXISTS"));

		assertThat(records.findAll()).hasSize(1).allMatch(record -> record.getOwner().getId().equals(ownerA));
		assertThat(wishlist.findAll()).isEmpty();
		assertThat(collections.findAll()).hasSize(1).allMatch(collection -> collection.getOwner().getId().equals(ownerA));
		assertThat(relations.findAll()).hasSize(1);
		assertThat(revisits.findAll()).hasSize(1).allMatch(revisit -> revisit.getOwner().getId().equals(ownerA));

		mvc.perform(post("/api/v1/archive/imports").with(accountA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(manifest("transfer-a")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.items[0].status").value("ALREADY_IMPORTED"))
			.andExpect(jsonPath("$.data.items[1].status").value("ALREADY_IMPORTED"));
		assertThat(records.findAll()).hasSize(1);
		assertThat(relations.findAll()).hasSize(1);

		mvc.perform(post("/api/v1/archive/imports").with(asAccount(ownerB)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(manifest("transfer-a")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("COMPLETE"));
		assertThat(records.findAll()).hasSize(2);
		assertThat(records.findAll()).extracting(record -> record.getOwner().getId()).containsExactlyInAnyOrder(ownerA, ownerB);
	}

	@Test
	void clientCannotChooseOwnerOrMixTransferIdentity() throws Exception {
		String accountId = account();
		mvc.perform(post("/api/v1/archive/imports").with(asAccount(accountId)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(manifest("transfer-a").replace("\"contractVersion\":\"v1\"", "\"contractVersion\":\"v1\",\"ownerId\":\"forged\"")))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		mvc.perform(post("/api/v1/archive/imports").with(asAccount(accountId)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(manifest("transfer-a").replaceFirst("\"transferId\":\"transfer-a\"", "\"transferId\":\"other\"")))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		assertThat(records.findAll()).isEmpty();
	}

	@Test
	void independentDeviceTransfersCanImportIntoTheSameAccount() throws Exception {
		String accountId = account();
		RequestPostProcessor owner = asAccount(accountId);

		mvc.perform(post("/api/v1/archive/imports").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(manifest("device-a")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("COMPLETE"));
		mvc.perform(post("/api/v1/archive/imports").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(manifest("device-b")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("COMPLETE"));

		assertThat(records.findAll()).hasSize(2).allMatch(record -> record.getOwner().getId().equals(accountId));
		assertThat(imports.findAll()).extracting(ArchiveImportItemEntity::getTransferId)
			.contains("device-a", "device-b");
	}

	@Test
	void importsMultipleMenusAndReplaysWithoutDuplicates() throws Exception {
		String accountId = account();
		RequestPostProcessor owner = asAccount(accountId);
		String payload = menuManifest("menu-transfer");

		mvc.perform(post("/api/v1/archive/imports").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(payload)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.cleanupAllowed").value(true));

		assertThat(recordMenus.findAll()).hasSize(3)
			.extracting(menu -> List.of(menu.getId(), menu.getName(), menu.getPrice(), (long) menu.getPosition()))
			.containsExactlyInAnyOrder(
				List.of("menu-a", "A", 0L, 0L),
				List.of("menu-b", "B", 15000L, 1L),
				List.of("menu-c", "C", 30000L, 2L));

		mvc.perform(post("/api/v1/archive/imports").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(payload)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.items[1].status").value("ALREADY_IMPORTED"));
		assertThat(recordMenus.findAll()).hasSize(3);
	}

	@Test
	void legacyPriceOnlyImportRemainsSupported() throws Exception {
		String accountId = account();
		mvc.perform(post("/api/v1/archive/imports").with(asAccount(accountId)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(legacyPriceOnlyManifest("price-only-transfer")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("COMPLETE"));

		assertThat(recordMenus.findAll()).singleElement().satisfies(menu -> {
			assertThat(menu.getName()).isNull();
			assertThat(menu.getPrice()).isEqualTo(9000L);
			assertThat(menu.getPosition()).isZero();
		});
	}

	private String account() { return accounts.saveAndFlush(AccountEntity.create()).getId(); }

	private RequestPostProcessor asAccount(String accountId) {
		return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(accountId), "n/a",
			List.of(new SimpleGrantedAuthority("ROLE_USER"))));
	}

	private String manifest(String transferId) {
		return """
			{"contractVersion":"v1","transferId":"%1$s","sourceSchemaVersion":2,"activeDiaryLocalId":"local-diary",
			"diaries":[{"transferId":"%1$s","sourceLocalId":"local-diary","entity":"diary","name":"Local","theme":"notebook","active":true}],
			"records":[{"transferId":"%1$s","sourceLocalId":"local-record","entity":"record","payload":{"id":"local-record","diaryId":"local-diary","type":"record","placeId":"place-a","placeName":"Place","category":"한식","date":"date","memo":"memo","address":"서울","visibility":"private","visitAt":"2026-09-12T00:00:00Z"}}],
			"wishlist":[{"transferId":"%1$s","sourceLocalId":"local-wish","entity":"wishlist","payload":{"id":"local-wish","diaryId":"local-diary","type":"wishlist","placeId":"place-a","placeName":"Place","category":"한식","date":"date","memo":"","address":"서울"}}],
			"revisits":[{"transferId":"%1$s","sourceLocalId":"local-revisit","entity":"revisit","payload":{"id":"local-revisit","diaryId":"local-diary","placeId":"place-a"}}],
			"collections":[{"transferId":"%1$s","sourceLocalId":"local-collection","entity":"collection","payload":{"id":"local-collection","diaryId":"local-diary","name":"Collection","memo":"","itemIds":[]}}],
			"relations":[{"transferId":"%1$s","sourceLocalId":"local-collection:local-record","entity":"collection-relation","collectionLocalId":"local-collection","itemLocalId":"local-record"}],"photos":[]}
			""".formatted(transferId);
	}

	private String menuManifest(String transferId) {
		return """
			{"contractVersion":"v1","transferId":"%1$s","sourceSchemaVersion":3,
			"diaries":[{"transferId":"%1$s","sourceLocalId":"local-diary","entity":"diary","name":"Local","theme":"notebook","active":true}],
			"records":[{"transferId":"%1$s","sourceLocalId":"local-record","entity":"record","payload":{"id":"local-record","diaryId":"local-diary","type":"record","placeId":"place-menu","placeName":"Place","category":"한식","date":"date","memo":"memo","address":"서울","visibility":"private","visitAt":"2026-09-12T00:00:00Z","menus":[{"id":"menu-a","name":"A","price":0,"position":0},{"id":"menu-b","name":"B","price":15000,"position":1},{"id":"menu-c","name":"C","price":30000,"position":2}]}}],
			"wishlist":[],"revisits":[],"collections":[],"relations":[],"photos":[]}
			""".formatted(transferId);
	}

	private String legacyPriceOnlyManifest(String transferId) {
		return """
			{"contractVersion":"v1","transferId":"%1$s","sourceSchemaVersion":2,
			"diaries":[{"transferId":"%1$s","sourceLocalId":"local-diary","entity":"diary","name":"Local","theme":"notebook","active":true}],
			"records":[{"transferId":"%1$s","sourceLocalId":"local-record","entity":"record","payload":{"id":"local-record","diaryId":"local-diary","type":"record","placeId":"place-price","placeName":"Place","category":"한식","date":"date","memo":"memo","address":"서울","visibility":"private","visitAt":"2026-09-12T00:00:00Z","price":9000}}],
			"wishlist":[],"revisits":[],"collections":[],"relations":[],"photos":[]}
			""".formatted(transferId);
	}
}
