package com.mytastelog.server.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.mytastelog.server.collection.CollectionRepository;
import com.mytastelog.server.exception.ApiErrorCode;
import com.mytastelog.server.exception.ApiException;
import com.mytastelog.server.record.RecordRepository;
import com.mytastelog.server.record.RecordEntity;
import com.mytastelog.server.record.RecordMenuEntity;
import com.mytastelog.server.record.RecordMenuRepository;
import com.mytastelog.server.wishlist.WishlistRepository;

import jakarta.persistence.EntityManager;

@Service
public class PhotoService {
	private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

	private final RecordRepository records;
	private final RecordMenuRepository recordMenus;
	private final WishlistRepository wishlist;
	private final CollectionRepository collections;
	private final PhotoFileValidator validator;
	private final PhotoStorage storage;
	private final EntityManager entityManager;

	public PhotoService(RecordRepository records, RecordMenuRepository recordMenus, WishlistRepository wishlist,
		CollectionRepository collections,
		PhotoFileValidator validator, PhotoStorage storage, EntityManager entityManager) {
		this.records = records;
		this.recordMenus = recordMenus;
		this.wishlist = wishlist;
		this.collections = collections;
		this.validator = validator;
		this.storage = storage;
		this.entityManager = entityManager;
	}

	@Transactional
	public PhotoResponse uploadRecord(String accountId, String id, MultipartFile file) {
		return upload(PhotoEntityType.RECORD, requireOwned(PhotoEntityType.RECORD, accountId, id), file);
	}

	@Transactional(readOnly = true)
	public PhotoContent readRecord(String accountId, String id) {
		return read(requireOwned(PhotoEntityType.RECORD, accountId, id));
	}

	@Transactional(readOnly = true)
	public PhotoContent readPublicRecord(RecordEntity record) {
		return read(record);
	}

	@Transactional
	public void deleteRecordPhoto(String accountId, String id) {
		remove(requireOwned(PhotoEntityType.RECORD, accountId, id));
	}

	@Transactional
	public PhotoResponse uploadRecordMenu(String accountId, String recordId, String menuId, MultipartFile file) {
		return upload(PhotoEntityType.RECORD_MENU, requireOwnedRecordMenu(accountId, recordId, menuId), file,
			recordMenuEndpoint(recordId, menuId));
	}

	@Transactional(readOnly = true)
	public PhotoContent readRecordMenu(String accountId, String recordId, String menuId) {
		return read(requireOwnedRecordMenu(accountId, recordId, menuId));
	}

	@Transactional
	public void deleteRecordMenuPhoto(String accountId, String recordId, String menuId) {
		remove(requireOwnedRecordMenu(accountId, recordId, menuId));
	}

	@Transactional
	public PhotoResponse uploadWishlist(String accountId, String id, MultipartFile file) {
		return upload(PhotoEntityType.WISHLIST, requireOwned(PhotoEntityType.WISHLIST, accountId, id), file);
	}

	@Transactional(readOnly = true)
	public PhotoContent readWishlist(String accountId, String id) {
		return read(requireOwned(PhotoEntityType.WISHLIST, accountId, id));
	}

	@Transactional
	public void deleteWishlistPhoto(String accountId, String id) {
		remove(requireOwned(PhotoEntityType.WISHLIST, accountId, id));
	}

	@Transactional
	public PhotoResponse uploadCollection(String accountId, String id, MultipartFile file) {
		return upload(PhotoEntityType.COLLECTION, requireOwned(PhotoEntityType.COLLECTION, accountId, id), file);
	}

	@Transactional(readOnly = true)
	public PhotoContent readCollection(String accountId, String id) {
		return read(requireOwned(PhotoEntityType.COLLECTION, accountId, id));
	}

	@Transactional
	public void deleteCollectionPhoto(String accountId, String id) {
		remove(requireOwned(PhotoEntityType.COLLECTION, accountId, id));
	}

	public boolean isManagedReference(String reference) {
		return storage.isManagedReference(reference);
	}

	public void cleanupAfterCommit(String reference) {
		if (!storage.isManagedReference(reference)) return;
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			deleteBestEffort(reference);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				deleteBestEffort(reference);
			}
		});
	}

	private PhotoResponse upload(PhotoEntityType type, PhotoReferenceOwner entity, MultipartFile file) {
		return upload(type, entity, file, endpoint(type, entity.getId()));
	}

	private PhotoResponse upload(PhotoEntityType type, PhotoReferenceOwner entity, MultipartFile file,
		String endpoint) {
		PhotoContent content = validator.validate(file);
		String oldReference = entity.getPhotoReference();
		String newReference;
		try {
			newReference = storage.store(type, content);
		} catch (PhotoStorageException exception) {
			throw storageApiException(exception);
		}
		registerReplacementCleanup(newReference, oldReference);
		try {
			entity.setPhotoReference(newReference);
			entityManager.flush();
		} catch (RuntimeException exception) {
			deleteBestEffort(newReference);
			throw exception;
		}
		return new PhotoResponse(true, endpoint);
	}

	private PhotoContent read(PhotoReferenceOwner entity) {
		String reference = entity.getPhotoReference();
		if (!storage.isManagedReference(reference)) throw noPhoto();
		try {
			return storage.read(reference);
		} catch (PhotoStorageException exception) {
			throw storageApiException(exception);
		}
	}

	private void remove(PhotoReferenceOwner entity) {
		String reference = entity.getPhotoReference();
		if (!storage.isManagedReference(reference)) throw noPhoto();
		try {
			storage.assertConfigured();
		} catch (PhotoStorageException exception) {
			throw storageApiException(exception);
		}
		entity.setPhotoReference(null);
		entityManager.flush();
		cleanupAfterCommit(reference);
	}

	private PhotoReferenceOwner requireOwned(PhotoEntityType type, String accountId, String id) {
		if (id == null || id.isBlank() || id.length() > 128) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
				"Resource ID는 비어 있지 않은 128자 이하 문자열이어야 합니다.", "id");
		}
		PhotoReferenceOwner entity = switch (type) {
			case RECORD -> records.findById(id).orElseThrow(() -> notFound(type));
			case RECORD_MENU -> throw new IllegalArgumentException("Record menu requires its parent Record ID");
			case WISHLIST -> wishlist.findById(id).orElseThrow(() -> notFound(type));
			case COLLECTION -> collections.findById(id).orElseThrow(() -> notFound(type));
		};
		if (!entity.getOwner().getId().equals(accountId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, "요청 권한이 없습니다.");
		}
		return entity;
	}

	private RecordMenuEntity requireOwnedRecordMenu(String accountId, String recordId, String menuId) {
		validateId(recordId, "recordId");
		validateId(menuId, "menuId");
		RecordEntity record = records.findById(recordId).orElseThrow(() -> notFound(PhotoEntityType.RECORD));
		if (!record.getOwner().getId().equals(accountId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, "요청 권한이 없습니다.");
		}
		return recordMenus.findById(menuId)
			.filter(menu -> menu.getRecord().getId().equals(recordId))
			.orElseThrow(() -> notFound(PhotoEntityType.RECORD_MENU));
	}

	private void validateId(String id, String field) {
		if (id == null || id.isBlank() || id.length() > 128) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
				"Resource ID는 비어 있지 않은 128자 이하 문자열이어야 합니다.", field);
		}
	}

	private void registerReplacementCleanup(String newReference, String oldReference) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				if (storage.isManagedReference(oldReference) && !oldReference.equals(newReference)) {
					deleteBestEffort(oldReference);
				}
			}

			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) deleteBestEffort(newReference);
			}
		});
	}

	private void deleteBestEffort(String reference) {
		try {
			storage.delete(reference);
		} catch (RuntimeException exception) {
			log.error("Managed photo cleanup failed key={} exceptionType={}", reference,
				exception.getClass().getSimpleName(), exception);
		}
	}

	private ApiException storageApiException(PhotoStorageException exception) {
		boolean configuration = exception.operation() == PhotoStorageException.Operation.CONFIGURATION;
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
			configuration ? ApiErrorCode.STORAGE_CONFIGURATION_ERROR : ApiErrorCode.STORAGE_ERROR,
			exception.getMessage());
	}

	private ApiException notFound(PhotoEntityType type) {
		return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND,
			switch (type) {
				case RECORD -> "Record를 찾을 수 없습니다.";
				case RECORD_MENU -> "Record Menu를 찾을 수 없습니다.";
				case WISHLIST -> "Wishlist를 찾을 수 없습니다.";
				case COLLECTION -> "Collection을 찾을 수 없습니다.";
			});
	}

	private ApiException noPhoto() {
		return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "서버에 저장된 사진이 없습니다.");
	}

	private String endpoint(PhotoEntityType type, String id) {
		return switch (type) {
			case RECORD -> "/api/v1/records/" + id + "/photo";
			case RECORD_MENU -> throw new IllegalArgumentException("Record menu endpoint requires its parent Record ID");
			case WISHLIST -> "/api/v1/wishlist/" + id + "/photo";
			case COLLECTION -> "/api/v1/collections/" + id + "/photo";
		};
	}

	private String recordMenuEndpoint(String recordId, String menuId) {
		return "/api/v1/records/" + recordId + "/menus/" + menuId + "/photo";
	}
}
