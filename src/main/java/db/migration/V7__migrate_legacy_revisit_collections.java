package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V7__migrate_legacy_revisit_collections extends BaseJavaMigration {
	private static final String LEGACY_NAME = "다시 가고 싶은 곳";
	private static final MigrationCheckpoint NO_OP_CHECKPOINT = () -> {};

	@Override
	public void migrate(Context context) throws Exception {
		migrate(context.getConnection());
	}

	@Override
	public boolean canExecuteInTransaction() {
		return false;
	}

	static void migrate(Connection connection) throws Exception {
		migrate(connection, NO_OP_CHECKPOINT);
	}

	static void migrate(Connection connection, MigrationCheckpoint checkpoint) throws Exception {
		if (!connection.getAutoCommit())
			throw new SQLException("V7 requires an auto-commit connection so it can own its transaction");

		connection.setAutoCommit(false);
		boolean transactionCompleted = false;
		Throwable failure = null;
		try {
			execute(connection, checkpoint);
			connection.commit();
			transactionCompleted = true;
		} catch (Throwable exception) {
			failure = exception;
			try {
				connection.rollback();
				transactionCompleted = true;
			} catch (SQLException rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
		}

		if (transactionCompleted) {
			try {
				connection.setAutoCommit(true);
			} catch (SQLException restoreFailure) {
				if (failure == null) failure = restoreFailure;
				else failure.addSuppressed(restoreFailure);
			}
		}

		if (failure != null) rethrow(failure);
	}

	private static void rethrow(Throwable failure) throws Exception {
		if (failure instanceof Exception exception) throw exception;
		if (failure instanceof Error error) throw error;
		throw new IllegalStateException("Unexpected migration failure", failure);
	}

	private static void execute(Connection connection, MigrationCheckpoint checkpoint) throws Exception {
		List<LegacyCollection> collections = findLegacyCollections(connection);
		if (collections.isEmpty()) return;

		Set<String> targetIds = new HashSet<>();
		for (LegacyCollection collection : collections) {
			targetIds.add(collection.id());
			if (collection.photoReference() != null)
				throw unsafe(collection, "Collection cover photo must be removed through the photo lifecycle before migration");
		}

		Set<RevisitKey> revisitKeys = new LinkedHashSet<>();
		List<SourceItem> sourceItems = new ArrayList<>();
		for (LegacyCollection collection : collections)
			validateAndCollect(connection, collection, revisitKeys, sourceItems);

		Set<Membership> preservedMemberships = findNonTargetMemberships(connection, sourceItems, targetIds);
		for (RevisitKey key : revisitKeys) createIfMissing(connection, key);
		checkpoint.afterRevisitWrites();
		for (RevisitKey key : revisitKeys) requireSingleRevisit(connection, key);

		for (LegacyCollection collection : collections) deleteCollection(connection, collection);

		for (SourceItem source : sourceItems) requireSourcePreserved(connection, source);
		for (Membership membership : preservedMemberships) requireMembershipPreserved(connection, membership);
	}

	private static List<LegacyCollection> findLegacyCollections(Connection connection) throws SQLException {
		List<LegacyCollection> result = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
			select id, owner_id, diary_id, photo_reference
			from collections
			where trim(name) = ?
			order by id
			for update
			""")) {
			statement.setString(1, LEGACY_NAME);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) result.add(new LegacyCollection(
					rows.getString("id"), rows.getString("owner_id"), rows.getString("diary_id"),
					rows.getString("photo_reference")));
			}
		}
		return result;
	}

	private static void validateAndCollect(Connection connection, LegacyCollection collection,
		Set<RevisitKey> revisitKeys, List<SourceItem> sourceItems) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			select item_id, item_type
			from collection_items
			where collection_id = ?
			order by position, item_id
			""")) {
			statement.setString(1, collection.id());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					String itemId = rows.getString("item_id");
					String itemType = rows.getString("item_type");
					SourceItem source = requireSource(connection, collection, itemId, itemType);
					sourceItems.add(source);
					if ("record".equals(itemType))
						revisitKeys.add(new RevisitKey(source.ownerId(), source.diaryId(), source.placeId()));
				}
			}
		}
	}

	private static SourceItem requireSource(Connection connection, LegacyCollection collection,
		String itemId, String itemType) throws SQLException {
		String table = switch (itemType) {
			case "record" -> "records";
			case "wishlist" -> "wishlist";
			default -> throw unsafe(collection, "Unsupported collection item type: " + itemType);
		};
		try (PreparedStatement statement = connection.prepareStatement(
			"select owner_id, diary_id, place_id from " + table + " where id = ?")) {
			statement.setString(1, itemId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw unsafe(collection, "Orphan " + itemType + " membership: " + itemId);
				String ownerId = rows.getString("owner_id");
				String diaryId = rows.getString("diary_id");
				if (!collection.ownerId().equals(ownerId) || !collection.diaryId().equals(diaryId))
					throw unsafe(collection, "Owner/diary mismatch for " + itemType + ": " + itemId);
				return new SourceItem(table, itemId, ownerId, diaryId, rows.getString("place_id"));
			}
		}
	}

	private static Set<Membership> findNonTargetMemberships(Connection connection, List<SourceItem> sources,
		Set<String> targetIds) throws SQLException {
		Set<Membership> result = new HashSet<>();
		try (PreparedStatement statement = connection.prepareStatement(
			"select collection_id from collection_items where item_id = ?")) {
			for (SourceItem source : sources) {
				statement.setString(1, source.id());
				try (ResultSet rows = statement.executeQuery()) {
					while (rows.next()) {
						String collectionId = rows.getString("collection_id");
						if (!targetIds.contains(collectionId)) result.add(new Membership(collectionId, source.id()));
					}
				}
			}
		}
		return result;
	}

	private static void createIfMissing(Connection connection, RevisitKey key) throws SQLException {
		if (countRevisits(connection, key) != 0) return;
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into revisit_intents(id, owner_id, diary_id, place_id, created_at)
			values (?, ?, ?, ?, current_timestamp)
			""")) {
			statement.setString(1, UUID.randomUUID().toString());
			statement.setString(2, key.ownerId());
			statement.setString(3, key.diaryId());
			statement.setString(4, key.placeId());
			statement.executeUpdate();
		}
	}

	private static void requireSingleRevisit(Connection connection, RevisitKey key) throws SQLException {
		if (countRevisits(connection, key) != 1)
			throw new SQLException("Expected exactly one revisit intent for " + key);
	}

	private static int countRevisits(Connection connection, RevisitKey key) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			select count(*)
			from revisit_intents
			where owner_id = ? and diary_id = ? and place_id = ?
			""")) {
			statement.setString(1, key.ownerId());
			statement.setString(2, key.diaryId());
			statement.setString(3, key.placeId());
			try (ResultSet rows = statement.executeQuery()) {
				rows.next();
				return rows.getInt(1);
			}
		}
	}

	private static void deleteCollection(Connection connection, LegacyCollection collection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"delete from collections where id = ? and owner_id = ? and diary_id = ?")) {
			statement.setString(1, collection.id());
			statement.setString(2, collection.ownerId());
			statement.setString(3, collection.diaryId());
			if (statement.executeUpdate() != 1) throw unsafe(collection, "Collection changed during migration");
		}
	}

	private static void requireSourcePreserved(Connection connection, SourceItem source) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"select count(*) from " + source.table() + " where id = ? and owner_id = ? and diary_id = ? and place_id = ?")) {
			statement.setString(1, source.id());
			statement.setString(2, source.ownerId());
			statement.setString(3, source.diaryId());
			statement.setString(4, source.placeId());
			try (ResultSet rows = statement.executeQuery()) {
				rows.next();
				if (rows.getInt(1) != 1) throw new SQLException("Source item changed during migration: " + source.id());
			}
		}
	}

	private static void requireMembershipPreserved(Connection connection, Membership membership) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"select count(*) from collection_items where collection_id = ? and item_id = ?")) {
			statement.setString(1, membership.collectionId());
			statement.setString(2, membership.itemId());
			try (ResultSet rows = statement.executeQuery()) {
				rows.next();
				if (rows.getInt(1) != 1) throw new SQLException("Non-target membership changed: " + membership);
			}
		}
	}

	private static SQLException unsafe(LegacyCollection collection, String reason) {
		return new SQLException("Unsafe legacy revisit collection " + collection.id() + ": " + reason);
	}

	private record LegacyCollection(String id, String ownerId, String diaryId, String photoReference) {}
	private record RevisitKey(String ownerId, String diaryId, String placeId) {}
	private record SourceItem(String table, String id, String ownerId, String diaryId, String placeId) {}
	private record Membership(String collectionId, String itemId) {}

	@FunctionalInterface
	interface MigrationCheckpoint {
		void afterRevisitWrites() throws Exception;
	}
}
