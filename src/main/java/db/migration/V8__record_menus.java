package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V8__record_menus extends BaseJavaMigration {
	private static final String ID_NAMESPACE = "mytastelog:record-menu:";

	@Override
	public void migrate(Context context) throws Exception {
		createTable(context.getConnection());
		backfill(context.getConnection());
	}

	@Override
	public boolean canExecuteInTransaction() {
		return false;
	}

	static void createTable(Connection connection) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.execute("""
				create table record_menus (
				    id varchar(128) primary key,
				    record_id varchar(128) not null,
				    name varchar(300),
				    price bigint,
				    position integer not null,
				    photo_reference varchar(512),
				    created_at timestamp(6) not null,
				    updated_at timestamp(6) not null,
				    constraint fk_record_menus_record foreign key (record_id) references records(id) on delete cascade,
				    constraint uk_record_menus_record_position unique (record_id, position),
				    constraint ck_record_menus_position check (position >= 0),
				    constraint ck_record_menus_price check (price is null or price >= 0)
				)
				""");
			statement.execute("create index idx_record_menus_record_position on record_menus(record_id, position)");
		}
	}

	static void backfill(Connection connection) throws Exception {
		try (PreparedStatement records = connection.prepareStatement("""
			select id, menu, price, created_at, updated_at
			from records
			where (menu is not null and trim(menu) <> '') or price is not null
			order by id
			""");
			PreparedStatement insert = connection.prepareStatement("""
				insert into record_menus(id, record_id, name, price, position, photo_reference, created_at, updated_at)
				values (?, ?, ?, ?, 0, null, ?, ?)
				""")) {
			try (ResultSet rows = records.executeQuery()) {
				while (rows.next()) {
					String recordId = rows.getString("id");
					String menu = rows.getString("menu");
					insert.setString(1, legacyMenuId(recordId));
					insert.setString(2, recordId);
					insert.setString(3, menu == null || menu.trim().isEmpty() ? null : menu);
					insert.setObject(4, rows.getObject("price"));
					insert.setTimestamp(5, rows.getTimestamp("created_at"));
					insert.setTimestamp(6, rows.getTimestamp("updated_at"));
					insert.addBatch();
				}
			}
			insert.executeBatch();
		}
	}

	public static String legacyMenuId(String recordId) {
		return UUID.nameUUIDFromBytes((ID_NAMESPACE + recordId).getBytes(StandardCharsets.UTF_8)).toString();
	}
}
