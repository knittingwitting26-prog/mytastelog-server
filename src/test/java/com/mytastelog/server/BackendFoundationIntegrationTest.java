package com.mytastelog.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountIdentityEntity;
import com.mytastelog.server.collection.CollectionEntity;
import com.mytastelog.server.collection.CollectionItemEntity;
import com.mytastelog.server.diary.DiaryEntity;
import com.mytastelog.server.record.RecordEntity;
import com.mytastelog.server.wishlist.WishlistEntity;
import com.mytastelog.server.revisit.RevisitIntentEntity;

import jakarta.persistence.EntityManagerFactory;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class BackendFoundationIntegrationTest {
	@Autowired MockMvc mockMvc;
	@Autowired DataSource dataSource;
	@Autowired EntityManagerFactory entityManagerFactory;
	@Autowired JdbcTemplate jdbcTemplate;

	@Test
	void healthIsPublicAndVersioned() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void futureArchiveNamespaceRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/archive"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.error.message").isNotEmpty());
	}

	@Test
	void databaseAndEntityMappingsStartCleanly() throws Exception {
		try (var connection = dataSource.getConnection()) {
			assertThat(connection.isValid(2)).isTrue();
		}
		var metamodel = entityManagerFactory.getMetamodel();
		assertThat(metamodel.entity(AccountEntity.class)).isNotNull();
		assertThat(metamodel.entity(AccountIdentityEntity.class)).isNotNull();
		assertThat(metamodel.entity(DiaryEntity.class)).isNotNull();
		assertThat(metamodel.entity(RecordEntity.class)).isNotNull();
		assertThat(metamodel.entity(WishlistEntity.class)).isNotNull();
		assertThat(metamodel.entity(CollectionEntity.class)).isNotNull();
		assertThat(metamodel.entity(CollectionItemEntity.class)).isNotNull();
		assertThat(metamodel.entity(RevisitIntentEntity.class)).isNotNull();
		assertThat(metamodel.entity(com.mytastelog.server.archive.ArchiveImportItemEntity.class)).isNotNull();
	}

	@Test
	void flywayBootstrapsTheExpectedSchemaExactlyOnce() {
		List<String> tables = jdbcTemplate.queryForList("""
			select table_name
			from information_schema.tables
			where table_schema = 'public'
			""", String.class);

		assertThat(tables).contains(
			"accounts", "account_identities", "diaries", "records", "wishlist",
			"collections", "collection_items", "archive_item_ids", "revisit_intents", "archive_import_items", "flyway_schema_history");
		assertThat(jdbcTemplate.queryForList("""
			select version
			from flyway_schema_history
			where success = true and version is not null
			order by installed_rank
			""", String.class)).containsExactly("1", "2", "3", "4", "5", "6");
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from information_schema.columns
			where table_schema = 'public'
			  and table_name = 'collections'
			  and column_name = 'photo_reference'
			""", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForList("""
			select column_name
			from information_schema.columns
			where table_schema = 'public'
			  and table_name in ('records', 'wishlist')
			  and column_name in ('latitude', 'longitude')
			""", String.class)).containsExactlyInAnyOrder("latitude", "longitude", "latitude", "longitude");
		assertThat(jdbcTemplate.queryForObject("""
			select is_identity
			from information_schema.columns
			where table_schema = 'public'
			  and table_name = 'account_identities'
			  and column_name = 'id'
			""", String.class)).isEqualTo("YES");
	}
}
