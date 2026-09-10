package com.mytastelog.server.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.repository.Repository;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.mytastelog.server.MytastelogServerApplication;
import com.mytastelog.server.account.AuthProvider;
import com.mytastelog.server.account.AuthenticatedAccount;
import com.mytastelog.server.auth.AuthenticationAccountService;
import com.mytastelog.server.auth.OAuthIdentity;

/** Explicit, opt-in verification of the existing AWS MySQL database. */
class AwsMySqlCrudVerificationTest {
	private static final String TARGET_DATABASE = "mytastelog";
	private static final List<String> APP_TABLES = List.of("accounts", "account_identities", "diaries",
		"records", "wishlist", "collections", "collection_items", "archive_item_ids", "revisit_intents");
	private static final Pattern MIGRATION = Pattern.compile("V(\\d+)__.+\\.sql");
	private static final Pattern FORWARDED_TLS_OPTION = Pattern.compile(
		"-D(javax\\.net\\.ssl\\.(?:trustStore|trustStorePassword|trustStoreType))="
			+ "(?:\\\"([^\\\"]*)\\\"|'([^']*)'|(\\S+))");
	private static final DateTimeFormatter MARKER_TIME =
		DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);

	private final Map<String, String> results = new LinkedHashMap<>();
	private final Set<String> accountIds = new LinkedHashSet<>();
	private final Set<String> identitySubjects = new LinkedHashSet<>();
	private Map<String, Long> baselineCounts = Map.of();
	private Set<String> knittingTablesBefore = Set.of();
	private Env env;
	private String marker;
	private String diaryId;
	private String recordId;
	private String record2Id;
	private String wishlistId;
	private String collectionId;
	private String injectionId;
	private boolean cleanupAuthorized;
	private ConfigurableApplicationContext context;
	private JdbcTemplate jdbc;
	private MockMvc mvc;

	@Test
	void verifyAwsMySqlCrud() throws Exception {
		if (!Boolean.parseBoolean(System.getProperty("awsVerification", "false"))) {
			System.out.println("AWS MySQL CRUD Verification: SKIPPED (explicit opt-in required)");
			Assumptions.abort("use -DawsVerification=true to opt in");
		}
		env = Env.fromProcess();
		if (!env.complete()) {
			System.out.println("AWS MySQL CRUD Verification");
			System.out.println("required AWS verification environment is missing");
			System.out.println("FINAL: SKIPPED");
			Assumptions.abort("required AWS verification environment is missing");
		}

		initializeMarker();
		Throwable failure = null;
		String stage = "Preflight";
		boolean cleanupPassed = false;
		try {
			preflight();
			pass("Preflight"); pass("Flyway V1"); pass("Flyway V2"); pass("Flyway no pending");
			stage = "JPA validate";
			startApplicationWithoutMigration();
			pass(stage);
			stage = "Archive CRUD";
			runCrud();
		} catch (Throwable problem) {
			failure = problem;
			results.put(stage, "FAIL");
		} finally {
			try {
				cleanupPassed = cleanupQaData();
			} catch (Throwable cleanupFailure) {
				if (failure == null) failure = cleanupFailure;
				else failure.addSuppressed(cleanupFailure);
			} finally {
				if (context != null) context.close();
			}
			results.put("Cleanup", cleanupPassed ? "PASS" : "FAIL");
			printReport(failure);
		}
		if (failure != null) fail(safeFailure(stage, failure));
	}

	private void initializeMarker() {
		marker = "AWS_VERIFY_" + MARKER_TIME.format(Instant.now()) + "_"
			+ UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
		diaryId = marker + "_DIARY";
		recordId = marker + "_RECORD";
		record2Id = marker + "_RECORD_2";
		wishlistId = marker + "_WISH";
		collectionId = marker + "_COLLECTION";
		injectionId = marker + "_INJECT";
		identitySubjects.add(marker + "_ACCOUNT_A");
		identitySubjects.add(marker + "_ACCOUNT_B");
	}

	private void applyForwardedTlsOptions() {
		String options = System.getenv("AWS_VERIFY_JAVA_OPTIONS");
		if (options == null || options.isBlank()) return;
		Matcher matcher = FORWARDED_TLS_OPTION.matcher(options);
		while (matcher.find()) {
			String value = matcher.group(2) != null ? matcher.group(2)
				: matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
			System.setProperty(matcher.group(1), value);
		}
	}

	private void preflight() throws Exception {
		URI uri = mysqlUri(env.url());
		assertThat(uri.getPath()).as("DB_URL database path").isEqualTo("/" + TARGET_DATABASE);
		applyForwardedTlsOptions();
		Class.forName("com.mysql.cj.jdbc.Driver");
		try (Connection connection = openConnection()) {
			connection.setReadOnly(true);
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
			assertThat(scalar(connection, "select database()")).isEqualTo(TARGET_DATABASE);
			assertThat(scalar(connection, "select version()")).startsWith("8.4.");
			assertThat(sessionStatus(connection, "Ssl_cipher")).as("TLS cipher").isNotBlank();
			Set<String> tables = strings(connection,
				"select table_name from information_schema.tables where table_schema = database()");
			assertThat(tables).containsAll(APP_TABLES).contains("flyway_schema_history");
			verifyHistory(connection);
			baselineCounts = tableCounts(connection);
			knittingTablesBefore = strings(connection,
				"select table_name from information_schema.tables where table_schema = 'knitting'");
		}
		assertOnlyV1AndV2Sources();
		validateFlywayReadOnly();
	}

	private void verifyHistory(Connection connection) throws SQLException {
		List<String> history = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(
			"select version, description, success from flyway_schema_history "
				+ "where version is not null order by installed_rank");
			ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				assertThat(rows.getBoolean("success")).as("Flyway version " + rows.getString("version")).isTrue();
				history.add(rows.getString("version") + ":" + rows.getString("description"));
			}
		}
		assertThat(history).containsExactly("1:backend foundation", "2:archive crud");
	}

	private void assertOnlyV1AndV2Sources() throws Exception {
		Path directory = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "db", "migration");
		List<Integer> versions;
		try (var files = Files.list(directory)) {
			versions = files.map(path -> path.getFileName().toString()).map(MIGRATION::matcher)
				.filter(Matcher::matches).map(match -> Integer.parseInt(match.group(1))).sorted().toList();
		}
		assertThat(versions).containsExactly(1, 2, 3, 4);
	}

	private void validateFlywayReadOnly() {
		ch.qos.logback.classic.Logger logger =
			(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.flywaydb");
		ch.qos.logback.classic.Level previous = logger.getLevel();
		logger.setLevel(ch.qos.logback.classic.Level.OFF);
		try {
			Flyway flyway = Flyway.configure().dataSource(env.url(), env.username(), env.password())
				.cleanDisabled(true).baselineOnMigrate(false).load();
			flyway.validate();
			assertThat(flyway.info().pending()).isEmpty();
		} finally {
			logger.setLevel(previous);
		}
	}

	private void startApplicationWithoutMigration() {
		context = new SpringApplicationBuilder(MytastelogServerApplication.class)
			.profiles("prod").web(WebApplicationType.SERVLET).run(
				"--spring.flyway.enabled=false", "--spring.jpa.hibernate.ddl-auto=validate", "--server.port=0",
				"--spring.main.banner-mode=off", "--spring.main.log-startup-info=false", "--logging.level.root=OFF",
				"--naver.local.client-id=aws-verification-placeholder",
				"--naver.local.client-secret=aws-verification-placeholder");
		assertThat(context.getBeansOfType(Repository.class)).hasSize(8);
		jdbc = new JdbcTemplate(context.getBean(DataSource.class));
		mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
	}

	private void runCrud() throws Exception {
		cleanupAuthorized = true;
		AuthenticationAccountService accounts = context.getBean(AuthenticationAccountService.class);
		List<String> subjects = identitySubjects.stream().toList();
		String ownerA = accounts.resolve(new OAuthIdentity(AuthProvider.NAVER, subjects.get(0))).accountId();
		assertThat(accounts.resolve(new OAuthIdentity(AuthProvider.NAVER, subjects.get(0))).accountId())
			.as("account identity uniqueness").isEqualTo(ownerA);
		String ownerB = accounts.resolve(new OAuthIdentity(AuthProvider.NAVER, subjects.get(1))).accountId();
		accountIds.addAll(List.of(ownerA, ownerB));
		RequestPostProcessor asA = asAccount(ownerA);
		RequestPostProcessor asB = asAccount(ownerB);

		postJson("/api/v1/diaries", asA, "{\"id\":\"" + injectionId
			+ "\",\"name\":\"Injected\",\"theme\":\"notebook\",\"ownerId\":\"" + ownerB + "\"}", 400);
		assertThat(count("diaries", injectionId)).isZero();
		postJson("/api/v1/diaries", asA, "{\"id\":\"" + diaryId + "\",\"name\":\"" + marker
			+ " Diary\",\"theme\":\"notebook\"}", 201);
		mvc.perform(get("/api/v1/diaries").with(asA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.id == '" + diaryId + "')]").exists());
		patchJson("/api/v1/diaries/{id}", diaryId, asA,
			"{\"name\":\"" + marker + " Diary Updated\",\"theme\":\"travel\"}", 200);
		patchJson("/api/v1/diaries/{id}", diaryId, asB, "{\"name\":\"foreign\"}", 404);
		assertOwnerAndTime("diaries", diaryId, ownerA);
		assertThat(value("select theme from diaries where id = ?", diaryId)).isEqualTo("travel");
		pass("Diary CRUD");

		postJson("/api/v1/records", asA, recordJson(recordId, marker + " Place"), 201);
		assertOwnerAndTime("records", recordId, ownerA);
		assertThat(count("archive_item_ids", recordId)).isOne();
		patchJson("/api/v1/records/{id}", recordId, asB, "{\"memo\":\"foreign\"}", 404);
		patchJson("/api/v1/records/{id}", recordId, asA,
			"{\"placeName\":\"" + marker + " Record Updated\",\"price\":12345}", 200);
		assertThat(value("select place_name from records where id = ?", recordId))
			.isEqualTo(marker + " Record Updated");

		postJson("/api/v1/wishlist", asA, wishlistJson(wishlistId), 201);
		assertOwnerAndTime("wishlist", wishlistId, ownerA);
		assertThat(count("archive_item_ids", wishlistId)).isOne();
		patchJson("/api/v1/wishlist/{id}", wishlistId, asB, "{\"memo\":\"foreign\"}", 404);
		patchJson("/api/v1/wishlist/{id}", wishlistId, asA,
			"{\"placeName\":\"" + marker + " Wish Updated\",\"memo\":\"updated\"}", 200);
		assertThat(value("select memo from wishlist where id = ?", wishlistId)).isEqualTo("updated");

		postJson("/api/v1/collections", asA, "{\"id\":\"" + collectionId + "\",\"diaryId\":\""
			+ diaryId + "\",\"name\":\"" + marker + " Collection\",\"memo\":\"qa\",\"itemIds\":[\""
			+ recordId + "\",\"" + wishlistId + "\"]}", 201);
		assertOwnerAndTime("collections", collectionId, ownerA);
		assertPositions(List.of(recordId, wishlistId));
		patchJson("/api/v1/collections/{id}", collectionId, asB, "{\"name\":\"foreign\"}", 404);
		mvc.perform(delete("/api/v1/collections/{id}", collectionId).with(asB).with(csrf()))
			.andExpect(status().isNotFound());
		patchJson("/api/v1/collections/{id}", collectionId, asA,
			"{\"name\":\"" + marker + " Updated\"}", 200);
		assertThat(value("select name from collections where id = ?", collectionId)).isEqualTo(marker + " Updated");

		postJson("/api/v1/records", asA, recordJson(record2Id, marker + " Second"), 201);
		mvc.perform(post("/api/v1/collections/{collectionId}/items/{itemId}", collectionId, record2Id)
			.with(asB).with(csrf())).andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/collections/{collectionId}/items/{itemId}", collectionId, record2Id)
			.with(asA).with(csrf())).andExpect(status().isOk());
		assertPositions(List.of(recordId, wishlistId, record2Id));
		mvc.perform(post("/api/v1/collections/{collectionId}/items/{itemId}", collectionId, record2Id)
			.with(asA).with(csrf())).andExpect(status().isConflict());
		mvc.perform(delete("/api/v1/collections/{collectionId}/items/{itemId}", collectionId, wishlistId)
			.with(asA).with(csrf())).andExpect(status().isNoContent());
		assertPositions(List.of(recordId, record2Id));
		pass("Collection Item");

		postJson("/api/v1/wishlist", asA, wishlistJson(recordId), 409);
		assertThat(count("wishlist", recordId)).isZero();
		pass("Constraints");

		mvc.perform(get("/api/v1/archive").with(asA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.diaries[?(@.id == '" + diaryId + "')]").exists())
			.andExpect(jsonPath("$.data.records[?(@.id == '" + recordId + "')]").exists())
			.andExpect(jsonPath("$.data.wishlist[?(@.id == '" + wishlistId + "')]").exists())
			.andExpect(jsonPath("$.data.collections[?(@.id == '" + collectionId + "')]").exists());
		mvc.perform(get("/api/v1/archive").with(asB)).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.diaries.length()").value(0))
			.andExpect(jsonPath("$.data.records.length()").value(0))
			.andExpect(jsonPath("$.data.wishlist.length()").value(0))
			.andExpect(jsonPath("$.data.collections.length()").value(0));
		mvc.perform(delete("/api/v1/records/{id}", recordId).with(asB).with(csrf()))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/wishlist/{id}", wishlistId).with(asB).with(csrf()))
			.andExpect(status().isNotFound());
		pass("Owner Isolation"); pass("Persistence");

		deleteAndAssert("/api/v1/records/{id}", recordId, asA, "records", "archive_item_ids");
		deleteAndAssert("/api/v1/records/{id}", record2Id, asA, "records", "archive_item_ids");
		deleteAndAssert("/api/v1/wishlist/{id}", wishlistId, asA, "wishlist", "archive_item_ids");
		deleteAndAssert("/api/v1/collections/{id}", collectionId, asA, "collections");
		pass("Record CRUD"); pass("Wishlist CRUD"); pass("Collection CRUD");
	}

	private boolean cleanupQaData() throws SQLException {
		if (!cleanupAuthorized) return true;
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			try {
				Set<String> fixtures = new LinkedHashSet<>(accountIds);
				fixtures.addAll(query(connection, "select account_id from account_identities where provider = 'naver' "
					+ "and provider_subject in (" + marks(identitySubjects.size()) + ")", identitySubjects));
				remove(connection, "collection_items", "collection_id", Set.of(collectionId));
				remove(connection, "collection_items", "item_id", Set.of(recordId, record2Id, wishlistId));
				remove(connection, "collections", "id", Set.of(collectionId));
				remove(connection, "archive_item_ids", "id", Set.of(recordId, record2Id, wishlistId));
				remove(connection, "records", "id", Set.of(recordId, record2Id));
				remove(connection, "wishlist", "id", Set.of(wishlistId, recordId));
				remove(connection, "diaries", "id", Set.of(diaryId, injectionId));
				removeIdentities(connection);
				remove(connection, "accounts", "id", fixtures);
				connection.commit();
				connection.setReadOnly(true);
				assertThat(tableCounts(connection)).isEqualTo(baselineCounts);
				assertThat(strings(connection,
					"select table_name from information_schema.tables where table_schema = 'knitting'"))
					.isEqualTo(knittingTablesBefore);
				assertThat(query(connection, "select provider_subject from account_identities where provider_subject in ("
					+ marks(identitySubjects.size()) + ")", identitySubjects)).isEmpty();
				return true;
			} catch (Throwable problem) {
				connection.rollback();
				throw problem;
			}
		}
	}

	private void postJson(String url, RequestPostProcessor owner, String json, int expected) throws Exception {
		mvc.perform(post(url).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().is(expected));
	}

	private void patchJson(String url, String id, RequestPostProcessor owner, String json, int expected)
		throws Exception {
		mvc.perform(patch(url, id).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().is(expected));
	}

	private void deleteAndAssert(String url, String id, RequestPostProcessor owner, String... tables)
		throws Exception {
		mvc.perform(delete(url, id).with(owner).with(csrf())).andExpect(status().isNoContent());
		for (String table : tables) assertThat(count(table, id)).isZero();
	}

	private String recordJson(String id, String name) {
		return "{\"id\":\"" + id + "\",\"diaryId\":\"" + diaryId
			+ "\",\"type\":\"record\",\"placeId\":\"" + marker + "_PLACE\",\"placeName\":\"" + name
			+ "\",\"category\":\"QA\",\"date\":\"2026-09-09\",\"memo\":\"qa\",\"address\":\"QA\","
			+ "\"visibility\":\"private\",\"visitAt\":\"2026-09-09T00:00:00Z\",\"rating\":4.5,"
			+ "\"menu\":\"QA\",\"price\":1000}";
	}

	private String wishlistJson(String id) {
		return "{\"id\":\"" + id + "\",\"diaryId\":\"" + diaryId
			+ "\",\"type\":\"wishlist\",\"placeId\":\"" + marker + "_WPLACE\",\"placeName\":\""
			+ marker + " Wish\",\"category\":\"QA\",\"date\":\"2026-09-09\",\"memo\":\"qa\","
			+ "\"address\":\"QA\"}";
	}

	private URI mysqlUri(String url) {
		assertThat(url).startsWith("jdbc:mysql://");
		URI uri = URI.create(url.substring("jdbc:".length()));
		assertThat(uri.getScheme()).isEqualTo("mysql");
		assertThat(uri.getUserInfo()).as("DB_URL must not contain credentials").isNull();
		return uri;
	}

	private Connection openConnection() throws SQLException {
		return DriverManager.getConnection(env.url(), env.username(), env.password());
	}

	private String scalar(Connection connection, String sql) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
			assertThat(row.next()).isTrue();
			return row.getString(1);
		}
	}

	private String sessionStatus(Connection connection, String name) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("show session status like ?")) {
			statement.setString(1, name);
			try (ResultSet row = statement.executeQuery()) {
				assertThat(row.next()).isTrue();
				return row.getString(2);
			}
		}
	}

	private Set<String> strings(Connection connection, String sql) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
			Set<String> values = new LinkedHashSet<>();
			while (rows.next()) values.add(rows.getString(1));
			return values;
		}
	}

	private Set<String> query(Connection connection, String sql, Set<String> parameters) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			int index = 1;
			for (String value : parameters) statement.setString(index++, value);
			try (ResultSet rows = statement.executeQuery()) {
				Set<String> values = new LinkedHashSet<>();
				while (rows.next()) values.add(rows.getString(1));
				return values;
			}
		}
	}

	private Map<String, Long> tableCounts(Connection connection) throws SQLException {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (String table : APP_TABLES) {
			try (PreparedStatement statement = connection.prepareStatement("select count(*) from " + table);
				ResultSet row = statement.executeQuery()) {
				row.next(); counts.put(table, row.getLong(1));
			}
		}
		return counts;
	}

	private void remove(Connection connection, String table, String column, Set<String> ids) throws SQLException {
		if (ids.isEmpty()) return;
		try (PreparedStatement statement = connection.prepareStatement(
			"delete from " + table + " where " + column + " in (" + marks(ids.size()) + ")")) {
			int index = 1;
			for (String id : ids) statement.setString(index++, id);
			statement.executeUpdate();
		}
	}

	private void removeIdentities(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"delete from account_identities where provider = 'naver' and provider_subject in ("
				+ marks(identitySubjects.size()) + ")")) {
			int index = 1;
			for (String subject : identitySubjects) statement.setString(index++, subject);
			statement.executeUpdate();
		}
	}

	private String marks(int size) {
		return String.join(",", Collections.nCopies(size, "?"));
	}

	private long count(String table, String id) {
		Long value = jdbc.queryForObject("select count(*) from " + table + " where id = ?", Long.class, id);
		return value == null ? 0 : value;
	}

	private String value(String sql, String id) {
		return jdbc.queryForObject(sql, String.class, id);
	}

	private void assertOwnerAndTime(String table, String id, String owner) {
		Map<String, Object> row = jdbc.queryForMap(
			"select owner_id, created_at, updated_at from " + table + " where id = ?", id);
		assertThat(row.get("owner_id")).isEqualTo(owner);
		assertThat(row.get("created_at")).isNotNull();
		assertThat(row.get("updated_at")).isNotNull();
	}

	private void assertPositions(List<String> ids) {
		List<Map<String, Object>> rows = jdbc.queryForList(
			"select item_id, position from collection_items where collection_id = ? order by position", collectionId);
		assertThat(rows).hasSize(ids.size());
		for (int index = 0; index < ids.size(); index++) {
			assertThat(rows.get(index).get("item_id")).isEqualTo(ids.get(index));
			assertThat(((Number) rows.get(index).get("position")).intValue()).isEqualTo(index);
		}
	}

	private RequestPostProcessor asAccount(String id) {
		AuthenticatedAccount principal = new AuthenticatedAccount(id);
		return authentication(new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities()));
	}

	private void pass(String stage) {
		results.put(stage, "PASS");
	}

	private void printReport(Throwable failure) {
		System.out.println();
		System.out.println("AWS MySQL CRUD Verification");
		results.forEach((stage, result) -> System.out.printf("%-20s %s%n", stage, result));
		if (failure == null && "PASS".equals(results.get("Cleanup"))) {
			System.out.println("FINAL: PASS");
		} else {
			System.out.println("FINAL: FAIL");
			System.out.println("Failure: " + safeFailure(null, failure));
			System.out.println("QA marker: " + marker);
			System.out.println("QA resource IDs: "
				+ String.join(", ", List.of(diaryId, recordId, record2Id, wishlistId, collectionId, injectionId)));
			System.out.println("Remaining QA resources must be checked using this marker.");
		}
	}

	private String safeFailure(String stage, Throwable failure) {
		if (failure == null) return stage == null ? "unknown failure" : stage;
		Throwable root = failure;
		while (root.getCause() != null && root.getCause() != root) root = root.getCause();
		String text = (stage == null ? "" : stage + " - ") + root.getClass().getSimpleName();
		if (root instanceof SQLException sql)
			text += " (SQLState=" + sql.getSQLState() + ", errorCode=" + sql.getErrorCode() + ")";
		return text;
	}

	private record Env(String url, String username, String password) {
		static Env fromProcess() {
			return new Env(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
		}

		boolean complete() {
			return Arrays.asList(url, username, password).stream()
				.allMatch(value -> value != null && !value.isBlank());
		}
	}
}
