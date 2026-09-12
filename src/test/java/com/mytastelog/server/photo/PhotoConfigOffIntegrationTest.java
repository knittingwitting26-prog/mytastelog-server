package com.mytastelog.server.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.account.AccountRepository;
import com.mytastelog.server.archive.ArchiveService;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.diary.DiaryTheme;
import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;
import com.mytastelog.server.record.RecordVisibility;

@SpringBootTest(properties = {
	"app.photo.s3.region=", "app.photo.s3.bucket="
})
@ActiveProfiles("test")
class PhotoConfigOffIntegrationTest {
	@Autowired PhotoService photos;
	@Autowired ArchiveService archive;
	@Autowired AccountRepository accounts;

	@Test
	void applicationStartsWithoutS3ConfigAndPhotoCallReturnsConfigurationError() {
		String owner = accounts.saveAndFlush(AccountEntity.create()).getId();
		archive.createDiary(owner, new CreateDiaryRequest("off-diary", "Diary", DiaryTheme.NOTEBOOK));
		archive.createRecord(owner, new CreateRecordRequest("off-record", "off-diary", "record", "place", "Record",
			"food", "date", "memo", "address", RecordVisibility.PRIVATE,
			Instant.parse("2026-09-08T03:00:00Z"), null, null, null, null, null));

		assertThatThrownBy(() -> photos.uploadRecord(owner, "off-record", new MockMultipartFile("file", "a.jpg",
			"image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})))
			.isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.STORAGE_CONFIGURATION_ERROR));
	}
}
