package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class V8RecordMenusMigrationTest {
	@Test
	void backfillsEveryLegacyShapeAndPreservesRecordColumnsAndRepresentativePhoto() {
		Fixture fixture = fixture();
		fixture.record("both", "제육볶음", 9000L, "photos/records/both.jpg");
		fixture.record("name-only", "라떼", null, null);
		fixture.record("price-only", null, 7000L, null);
		fixture.record("blank", "   ", null, null);
		fixture.record("zero", "무료 메뉴", 0L, null);

		fixture.migrate();

		assertThat(fixture.jdbc.queryForObject("select count(*) from record_menus", Integer.class)).isEqualTo(4);
		assertThat(fixture.jdbc.queryForMap("select * from record_menus where record_id = 'both'"))
			.containsEntry("id", V8__record_menus.legacyMenuId("both"))
			.containsEntry("name", "제육볶음")
			.containsEntry("price", 9000L)
			.containsEntry("position", 0)
			.containsEntry("photo_reference", null);
		assertThat(fixture.jdbc.queryForMap("select name, price from record_menus where record_id = 'price-only'"))
			.containsEntry("name", null).containsEntry("price", 7000L);
		assertThat(fixture.jdbc.queryForObject("select price from record_menus where record_id = 'zero'", Long.class))
			.isZero();
		assertThat(fixture.jdbc.queryForObject("select count(*) from record_menus where record_id = 'blank'", Integer.class))
			.isZero();
		assertThat(fixture.jdbc.queryForMap("select menu, price, photo_reference from records where id = 'both'"))
			.containsEntry("menu", "제육볶음").containsEntry("price", 9000L)
			.containsEntry("photo_reference", "photos/records/both.jpg");
	}

	@Test
	void deterministicIdsAreStableAndDifferentAcrossRecords() {
		assertThat(new V8__record_menus().canExecuteInTransaction()).isFalse();
		assertThat(V8__record_menus.legacyMenuId("record-a"))
			.isEqualTo(V8__record_menus.legacyMenuId("record-a"))
			.isNotEqualTo(V8__record_menus.legacyMenuId("record-b"));
	}

	@Test
	void constraintsProtectForeignKeysPositionsAndPrices() {
		Fixture fixture = fixture();
		fixture.record("record-a", null, null, null);
		fixture.migrate();
		fixture.insertMenu("menu-a", "record-a", 0, 0L);

		assertThatThrownBy(() -> fixture.insertMenu("menu-orphan", "missing", 0, null))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> fixture.insertMenu("menu-position", "record-a", 0, null))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> fixture.insertMenu("menu-negative-position", "record-a", -1, null))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> fixture.insertMenu("menu-negative-price", "record-a", 1, -1L))
			.isInstanceOf(DataIntegrityViolationException.class);

		fixture.jdbc.update("delete from records where id = 'record-a'");
		assertThat(fixture.jdbc.queryForObject("select count(*) from record_menus", Integer.class)).isZero();
	}

	private Fixture fixture() {
		String url = "jdbc:h2:mem:menus-" + UUID.randomUUID()
			+ ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
		Flyway.configure().dataSource(url, "sa", "").target(MigrationVersion.fromVersion("7")).load().migrate();
		Fixture fixture = new Fixture(url);
		fixture.jdbc.update("insert into accounts(id, created_at, updated_at) values ('owner', current_timestamp, current_timestamp)");
		fixture.jdbc.update("insert into diaries(id, owner_id, name, theme, created_at, updated_at) values "
			+ "('diary', 'owner', 'Diary', 'notebook', current_timestamp, current_timestamp)");
		return fixture;
	}

	private static final class Fixture {
		private final String url;
		private final JdbcTemplate jdbc;

		private Fixture(String url) {
			this.url = url;
			this.jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
		}

		private void record(String id, String menu, Long price, String photo) {
			jdbc.update("""
				insert into records(id, owner_id, diary_id, place_id, place_name, category, date_display, memo,
				address, menu, price, photo_reference, visibility, visit_at, created_at, updated_at)
				values (?, 'owner', 'diary', ?, 'Place', 'food', '', '', '', ?, ?, ?, 'private',
				current_timestamp, current_timestamp, current_timestamp)
				""", id, "place-" + id, menu, price, photo);
		}

		private void migrate() {
			Flyway.configure().dataSource(url, "sa", "").load().migrate();
		}

		private void insertMenu(String id, String recordId, int position, Long price) {
			jdbc.update("""
				insert into record_menus(id, record_id, name, price, position, created_at, updated_at)
				values (?, ?, 'menu', ?, ?, current_timestamp, current_timestamp)
				""", id, recordId, price, position);
		}
	}
}
