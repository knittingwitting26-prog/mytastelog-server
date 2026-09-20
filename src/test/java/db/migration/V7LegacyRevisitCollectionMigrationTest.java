package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class V7LegacyRevisitCollectionMigrationTest {
	@Test
	void productionShapeCreatesFourRevisitsAndPreservesAllSourcesAndOtherMemberships() throws Exception {
		Fixture fixture = fixture();
		fixture.account("owner-a");
		fixture.diary("diary-a", "owner-a");
		fixture.collection("legacy-a", "owner-a", "diary-a", " 다시 가고 싶은 곳 ", 0);
		fixture.collection("other-a", "owner-a", "diary-a", "맛집", 1);

		fixture.record("record-1", "owner-a", "diary-a", "place-1");
		fixture.record("record-2", "owner-a", "diary-a", "place-1");
		fixture.record("record-3", "owner-a", "diary-a", "place-2");
		fixture.record("record-4", "owner-a", "diary-a", "place-3");
		fixture.record("record-5", "owner-a", "diary-a", "place-4");
		fixture.wishlist("wish-1", "owner-a", "diary-a", "wish-place-1");
		fixture.wishlist("wish-2", "owner-a", "diary-a", "wish-place-2");
		fixture.items("legacy-a", "record-1", "record-2", "record-3", "record-4", "record-5", "wish-1", "wish-2");
		fixture.item("other-a", "record-1", "record", 0);

		fixture.migrate();

		assertThat(fixture.count("revisit_intents")).isEqualTo(4);
		assertThat(fixture.count("records")).isEqualTo(5);
		assertThat(fixture.count("wishlist")).isEqualTo(2);
		assertThat(fixture.countWhere("collections", "id = 'legacy-a'")).isZero();
		assertThat(fixture.countWhere("collection_items", "collection_id = 'legacy-a'")).isZero();
		assertThat(fixture.countWhere("collection_items", "collection_id = 'other-a' and item_id = 'record-1'")).isOne();
		assertThat(fixture.countWhere("revisit_intents", "place_id like 'wish-place-%'")).isZero();
	}

	@Test
	void existingRevisitIsReusedAndSameNamedCollectionsStayOwnerAndDiaryScoped() throws Exception {
		Fixture fixture = fixture();
		fixture.account("owner-a");
		fixture.account("owner-b");
		fixture.diary("diary-a", "owner-a");
		fixture.diary("diary-b", "owner-b");
		fixture.collection("legacy-a", "owner-a", "diary-a", "다시 가고 싶은 곳", 0);
		fixture.collection("legacy-b", "owner-b", "diary-b", "다시 가고 싶은 곳", 0);
		fixture.record("record-a", "owner-a", "diary-a", "same-place");
		fixture.record("record-b", "owner-b", "diary-b", "same-place");
		fixture.item("legacy-a", "record-a", "record", 0);
		fixture.item("legacy-b", "record-b", "record", 0);
		fixture.revisit("existing-a", "owner-a", "diary-a", "same-place");

		fixture.migrate();

		assertThat(fixture.count("revisit_intents")).isEqualTo(2);
		assertThat(fixture.countWhere("revisit_intents", "owner_id = 'owner-a' and diary_id = 'diary-a'")).isOne();
		assertThat(fixture.countWhere("revisit_intents", "owner_id = 'owner-b' and diary_id = 'diary-b'")).isOne();
	}

	@Test
	void rerunIsANoOp() throws Exception {
		Fixture fixture = fixture();
		fixture.account("owner-a");
		fixture.diary("diary-a", "owner-a");
		fixture.collection("legacy-a", "owner-a", "diary-a", "다시 가고 싶은 곳", 0);
		fixture.record("record-a", "owner-a", "diary-a", "place-a");
		fixture.item("legacy-a", "record-a", "record", 0);
		fixture.migrate();

		try (Connection connection = fixture.connection()) {
			V7__migrate_legacy_revisit_collections.migrate(connection);
		}

		assertThat(fixture.count("revisit_intents")).isOne();
		assertThat(fixture.count("records")).isOne();
	}

	@Test
	void unsafeMembershipStopsEveryTargetBeforeAnyWrite() {
		Fixture fixture = fixture();
		fixture.account("owner-a");
		fixture.diary("diary-a", "owner-a");
		fixture.collection("legacy-a", "owner-a", "diary-a", "다시 가고 싶은 곳", 0);
		fixture.collection("legacy-z", "owner-a", "diary-a", "다시 가고 싶은 곳", 1);
		fixture.record("record-a", "owner-a", "diary-a", "place-a");
		fixture.item("legacy-a", "record-a", "record", 0);
		fixture.item("legacy-z", "missing-record", "record", 0);

		assertThatThrownBy(fixture::migrate).hasMessageContaining("Orphan record membership");

		assertThat(fixture.count("revisit_intents")).isZero();
		assertThat(fixture.countWhere("collections", "id in ('legacy-a', 'legacy-z')")).isEqualTo(2);
		assertThat(fixture.count("records")).isOne();
	}

	@Test
	void collectionCoverPhotoStopsMigrationBeforeAnyWrite() {
		Fixture fixture = fixture();
		fixture.account("owner-a");
		fixture.diary("diary-a", "owner-a");
		fixture.collection("legacy-a", "owner-a", "diary-a", "다시 가고 싶은 곳", 0);
		fixture.record("record-a", "owner-a", "diary-a", "place-a");
		fixture.item("legacy-a", "record-a", "record", 0);
		fixture.collectionPhoto("legacy-a", "photos/collections/legacy-a.jpg");

		assertThatThrownBy(fixture::migrate).hasMessageContaining("Collection cover photo");

		assertThat(fixture.count("revisit_intents")).isZero();
		assertThat(fixture.countWhere("collections", "id = 'legacy-a'")).isOne();
		assertThat(fixture.count("records")).isOne();
	}

	@Test
	void sqlExceptionAfterRevisitCreationRollsBackEveryChange() {
		Fixture fixture = fixture();
		fixture.account("owner-a");
		fixture.diary("diary-a", "owner-a");
		fixture.collection("legacy-a", "owner-a", "diary-a", "다시 가고 싶은 곳", 0);
		fixture.record("record-a", "owner-a", "diary-a", "place-a");
		fixture.wishlist("wish-a", "owner-a", "diary-a", "wish-place-a");
		fixture.item("legacy-a", "record-a", "record", 0);
		fixture.item("legacy-a", "wish-a", "wishlist", 1);

		assertThatThrownBy(() -> fixture.migrateDirect(
			() -> { throw new SQLException("injected failure after revisit creation"); }))
			.isInstanceOf(SQLException.class).hasMessageContaining("injected failure");

		assertThat(fixture.count("revisit_intents")).isZero();
		assertThat(fixture.countWhere("collections", "id = 'legacy-a'")).isOne();
		assertThat(fixture.count("records")).isOne();
		assertThat(fixture.count("wishlist")).isOne();
	}

	@Test
	void runtimeExceptionAfterRevisitCreationRollsBackEveryChange() {
		Fixture fixture = fixture();
		fixture.account("owner-a");
		fixture.diary("diary-a", "owner-a");
		fixture.collection("legacy-a", "owner-a", "diary-a", "다시 가고 싶은 곳", 0);
		fixture.record("record-a", "owner-a", "diary-a", "place-a");
		fixture.wishlist("wish-a", "owner-a", "diary-a", "wish-place-a");
		fixture.item("legacy-a", "record-a", "record", 0);
		fixture.item("legacy-a", "wish-a", "wishlist", 1);

		assertThatThrownBy(() -> fixture.migrateDirect(
			() -> { throw new IllegalStateException("injected unchecked failure"); }))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("injected unchecked failure");

		assertThat(fixture.count("revisit_intents")).isZero();
		assertThat(fixture.countWhere("collections", "id = 'legacy-a'")).isOne();
		assertThat(fixture.count("records")).isOne();
		assertThat(fixture.count("wishlist")).isOne();
	}

	@Test
	void existingTransactionIsRejectedWithoutCommitOrRollback() throws Exception {
		Fixture fixture = fixture();
		fixture.account("owner-a");
		fixture.diary("diary-a", "owner-a");
		fixture.collection("legacy-a", "owner-a", "diary-a", "다시 가고 싶은 곳", 0);
		fixture.record("record-a", "owner-a", "diary-a", "place-a");
		fixture.item("legacy-a", "record-a", "record", 0);

		try (Connection connection = fixture.connection()) {
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(
				"insert into accounts(id, created_at, updated_at) values (?, current_timestamp, current_timestamp)")) {
				statement.setString(1, "external-transaction-marker");
				statement.executeUpdate();
			}

			assertThatThrownBy(() -> V7__migrate_legacy_revisit_collections.migrate(connection))
				.isInstanceOf(SQLException.class).hasMessageContaining("requires an auto-commit connection");
			assertThat(connection.getAutoCommit()).isFalse();
			assertThat(fixture.countWhere("accounts", "id = 'external-transaction-marker'")).isZero();
			try (PreparedStatement statement = connection.prepareStatement(
				"select count(*) from accounts where id = ?")) {
				statement.setString(1, "external-transaction-marker");
				try (var rows = statement.executeQuery()) {
					rows.next();
					assertThat(rows.getInt(1)).isOne();
				}
			}
			connection.rollback();
		}

		assertThat(fixture.countWhere("accounts", "id = 'external-transaction-marker'")).isZero();
		assertThat(fixture.count("revisit_intents")).isZero();
		assertThat(fixture.countWhere("collections", "id = 'legacy-a'")).isOne();
	}

	@Test
	void deleteFailureRollsBackRevisitCreation() {
		Fixture fixture = fixture();
		fixture.account("owner-a");
		fixture.diary("diary-a", "owner-a");
		fixture.collection("legacy-a", "owner-a", "diary-a", "다시 가고 싶은 곳", 0);
		fixture.record("record-a", "owner-a", "diary-a", "place-a");
		fixture.item("legacy-a", "record-a", "record", 0);
		fixture.blockCollectionDelete("legacy-a");

		assertThatThrownBy(fixture::migrate).isInstanceOf(RuntimeException.class);

		assertThat(fixture.count("revisit_intents")).isZero();
		assertThat(fixture.countWhere("collections", "id = 'legacy-a'")).isOne();
		assertThat(fixture.count("records")).isOne();
	}

	private Fixture fixture() {
		String url = "jdbc:h2:mem:legacy-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
		Flyway.configure().dataSource(url, "sa", "").target(MigrationVersion.fromVersion("6")).load().migrate();
		return new Fixture(url);
	}

	private static final class Fixture {
		private final String url;
		private final JdbcTemplate jdbc;

		private Fixture(String url) {
			this.url = url;
			this.jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
		}

		private Connection connection() throws SQLException {
			return DriverManager.getConnection(url, "sa", "");
		}

		private void migrate() {
			Flyway.configure().dataSource(url, "sa", "").load().migrate();
		}

		private void migrateDirect(V7__migrate_legacy_revisit_collections.MigrationCheckpoint checkpoint)
			throws Exception {
			try (Connection connection = connection()) {
				V7__migrate_legacy_revisit_collections.migrate(connection, checkpoint);
			}
		}

		private void account(String id) {
			jdbc.update("insert into accounts(id, created_at, updated_at) values (?, current_timestamp, current_timestamp)", id);
		}

		private void diary(String id, String owner) {
			jdbc.update("insert into diaries(id, owner_id, name, theme, created_at, updated_at) values (?, ?, 'Diary', 'notebook', current_timestamp, current_timestamp)", id, owner);
		}

		private void collection(String id, String owner, String diary, String name, int position) {
			jdbc.update("insert into collections(id, owner_id, diary_id, name, memo, created_at, updated_at, position) values (?, ?, ?, ?, '', current_timestamp, current_timestamp, ?)", id, owner, diary, name, position);
		}

		private void record(String id, String owner, String diary, String place) {
			jdbc.update("""
				insert into records(id, owner_id, diary_id, place_id, place_name, category, date_display, memo, address,
				visibility, visit_at, created_at, updated_at)
				values (?, ?, ?, ?, 'Place', 'food', '', '', '', 'private', current_timestamp, current_timestamp, current_timestamp)
				""", id, owner, diary, place);
		}

		private void wishlist(String id, String owner, String diary, String place) {
			jdbc.update("""
				insert into wishlist(id, owner_id, diary_id, place_id, place_name, category, date_display, memo, address,
				created_at, updated_at)
				values (?, ?, ?, ?, 'Place', 'food', '', '', '', current_timestamp, current_timestamp)
				""", id, owner, diary, place);
		}

		private void items(String collection, String... ids) {
			for (int index = 0; index < ids.length; index++)
				item(collection, ids[index], ids[index].startsWith("wish-") ? "wishlist" : "record", index);
		}

		private void item(String collection, String item, String type, int position) {
			jdbc.update("insert into collection_items(collection_id, item_id, item_type, position) values (?, ?, ?, ?)",
				collection, item, type, position);
		}

		private void revisit(String id, String owner, String diary, String place) {
			jdbc.update("insert into revisit_intents(id, owner_id, diary_id, place_id, created_at) values (?, ?, ?, ?, current_timestamp)", id, owner, diary, place);
		}

		private void collectionPhoto(String collectionId, String reference) {
			jdbc.update("update collections set photo_reference = ? where id = ?", reference, collectionId);
		}

		private void blockCollectionDelete(String collectionId) {
			jdbc.execute("create table migration_delete_blocker(collection_id varchar(128) primary key, constraint fk_migration_delete_blocker foreign key (collection_id) references collections(id))");
			jdbc.update("insert into migration_delete_blocker(collection_id) values (?)", collectionId);
		}

		private int count(String table) {
			return jdbc.queryForObject("select count(*) from " + table, Integer.class);
		}

		private int countWhere(String table, String condition) {
			return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
		}
	}
}
