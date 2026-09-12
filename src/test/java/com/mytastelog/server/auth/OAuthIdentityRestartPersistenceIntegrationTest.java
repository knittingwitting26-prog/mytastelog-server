package com.mytastelog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

import com.mytastelog.server.MytastelogServerApplication;
import com.mytastelog.server.account.AccountIdentityRepository;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.account.AuthProvider;
import com.mytastelog.server.archive.ArchiveService;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.diary.DiaryRepository;
import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordVisibility;

class OAuthIdentityRestartPersistenceIntegrationTest {

	@TempDir
	java.nio.file.Path tempDirectory;

	@Test
	void localProfileDefaultsToFileDatabaseInsteadOfVolatileMemory() throws Exception {
		var source = new YamlPropertySourceLoader()
			.load("application-local", new ClassPathResource("application-local.yaml")).getFirst();
		String url = (String) source.getProperty("spring.datasource.url");

		assertThat(url).contains("jdbc:h2:file:").doesNotContain("jdbc:h2:mem:");
	}

	@Test
	void persistedIdentityAndArchiveSurviveApplicationContextRestart() {
		String databaseUrl = "jdbc:h2:file:"
			+ tempDirectory.resolve("relogin").toAbsolutePath().toString().replace('\\', '/')
			+ ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
		String firstAccountId;

		try (ConfigurableApplicationContext first = start(databaseUrl)) {
			AuthenticationAccountService authentication = first.getBean(AuthenticationAccountService.class);
			ArchiveService archive = first.getBean(ArchiveService.class);
			firstAccountId = authentication.resolve(
				new OAuthIdentity(AuthProvider.GOOGLE, "restart-stable-subject")).accountId();
			archive.createDiary(firstAccountId,
				new CreateDiaryRequest("restart-diary", "Persistent diary", DiaryTheme.NOTEBOOK));
			archive.createRecord(firstAccountId, new CreateRecordRequest(
				"restart-record", "restart-diary", "record", "place", "Persistent record", "food",
				"2026-09-10", "memo", "Seoul", RecordVisibility.PRIVATE,
				Instant.parse("2026-09-10T03:00:00Z"), null, null, null, null, null));
		}

		try (ConfigurableApplicationContext second = start(databaseUrl)) {
			AuthenticationAccountService authentication = second.getBean(AuthenticationAccountService.class);
			String secondAccountId = authentication.resolve(
				new OAuthIdentity(AuthProvider.GOOGLE, "restart-stable-subject")).accountId();

			assertThat(secondAccountId).isEqualTo(firstAccountId);
			assertThat(second.getBean(AccountRepository.class).count()).isEqualTo(1);
			assertThat(second.getBean(AccountIdentityRepository.class).count()).isEqualTo(1);
			assertThat(second.getBean(DiaryRepository.class)
				.findByIdAndOwner_Id("restart-diary", secondAccountId)).isPresent();
			assertThat(second.getBean(RecordRepository.class)
				.findByIdAndOwner_Id("restart-record", secondAccountId)).isPresent();
			assertThat(second.getBean(ArchiveService.class).loadArchive(secondAccountId).records())
				.singleElement().extracting(record -> record.id()).isEqualTo("restart-record");
		}
	}

	private ConfigurableApplicationContext start(String databaseUrl) {
		return new SpringApplicationBuilder(MytastelogServerApplication.class)
			.profiles("local")
			.web(WebApplicationType.SERVLET)
			.run(
				"--server.port=0",
				"--spring.datasource.url=" + databaseUrl);
	}
}
