package com.mytastelog.server.photo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.mytastelog.server.account.AccountEntity;
import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.record.RecordEntity;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.wishlist.WishlistRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

class PhotoServiceFailureTest {
	@Test
	void databaseFlushFailureBestEffortDeletesNewObject() {
		RecordRepository records = mock(RecordRepository.class);
		WishlistRepository wishlist = mock(WishlistRepository.class);
		CollectionRepository collections = mock(CollectionRepository.class);
		PhotoStorage storage = mock(PhotoStorage.class);
		EntityManager entityManager = mock(EntityManager.class);
		RecordEntity record = mock(RecordEntity.class);
		AccountEntity owner = mock(AccountEntity.class);
		when(record.getOwner()).thenReturn(owner);
		when(owner.getId()).thenReturn("owner");
		when(record.getPhotoReference()).thenReturn("photos/records/550e8400-e29b-41d4-a716-446655440000.jpg");
		when(records.findById("record")).thenReturn(Optional.of(record));
		String newKey = "photos/records/550e8400-e29b-41d4-a716-446655440001.jpg";
		when(storage.store(org.mockito.ArgumentMatchers.eq(PhotoEntityType.RECORD),
			org.mockito.ArgumentMatchers.any(PhotoContent.class))).thenReturn(newKey);
		when(storage.isManagedReference(org.mockito.ArgumentMatchers.any())).thenReturn(true);
		org.mockito.Mockito.doThrow(new PersistenceException("db failed")).when(entityManager).flush();
		PhotoService service = new PhotoService(records, wishlist, collections, new PhotoFileValidator(), storage,
			entityManager);

		assertThatThrownBy(() -> service.uploadRecord("owner", "record", new MockMultipartFile("file", "x.jpg",
			"image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})))
			.isInstanceOf(PersistenceException.class);
		verify(storage).delete(newKey);
	}
}
