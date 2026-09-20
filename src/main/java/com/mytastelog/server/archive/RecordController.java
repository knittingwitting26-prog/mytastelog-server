package com.mytastelog.server.archive;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.mytastelog.server.account.AuthenticatedAccountContext;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.UpdateRecordRequest;
import com.mytastelog.server.archive.dto.ArchiveResponses.ApiSuccess;
import com.mytastelog.server.archive.dto.ArchiveResponses.RecordResponse;
import com.mytastelog.server.photo.PhotoResponse;
import com.mytastelog.server.photo.PhotoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/records")
public class RecordController {
	private final ArchiveService service;
	private final AuthenticatedAccountContext accountContext;
	private final PhotoService photos;

	public RecordController(ArchiveService service, AuthenticatedAccountContext accountContext, PhotoService photos) {
		this.service = service;
		this.accountContext = accountContext;
		this.photos = photos;
	}

	@PostMapping
	ResponseEntity<ApiSuccess<RecordResponse>> create(Authentication authentication,
		@Valid @RequestBody CreateRecordRequest request) {
		var result = service.createRecord(accountContext.requireAccountId(authentication), request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(new ApiSuccess<>(result.value()));
	}

	@PatchMapping("/{id}")
	ApiSuccess<RecordResponse> update(Authentication authentication, @PathVariable String id,
		@RequestBody UpdateRecordRequest request) {
		return new ApiSuccess<>(service.updateRecord(accountContext.requireAccountId(authentication), id, request));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(Authentication authentication, @PathVariable String id) {
		service.deleteRecord(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ApiSuccess<PhotoResponse> uploadPhoto(Authentication authentication, @PathVariable String id,
		@RequestPart("file") MultipartFile file) {
		return new ApiSuccess<>(photos.uploadRecord(accountContext.requireAccountId(authentication), id, file));
	}

	@GetMapping("/{id}/photo")
	ResponseEntity<byte[]> readPhoto(Authentication authentication, @PathVariable String id) {
		var photo = photos.readRecord(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
			.cacheControl(CacheControl.noStore()).contentLength(photo.bytes().length).body(photo.bytes());
	}

	@DeleteMapping("/{id}/photo")
	ResponseEntity<Void> deletePhoto(Authentication authentication, @PathVariable String id) {
		photos.deleteRecordPhoto(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(value = "/{recordId}/menus/{menuId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ApiSuccess<PhotoResponse> uploadMenuPhoto(Authentication authentication, @PathVariable String recordId,
		@PathVariable String menuId, @RequestPart("file") MultipartFile file) {
		return new ApiSuccess<>(photos.uploadRecordMenu(accountContext.requireAccountId(authentication), recordId,
			menuId, file));
	}

	@GetMapping("/{recordId}/menus/{menuId}/photo")
	ResponseEntity<byte[]> readMenuPhoto(Authentication authentication, @PathVariable String recordId,
		@PathVariable String menuId) {
		var photo = photos.readRecordMenu(accountContext.requireAccountId(authentication), recordId, menuId);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
			.cacheControl(CacheControl.noStore()).contentLength(photo.bytes().length).body(photo.bytes());
	}

	@DeleteMapping("/{recordId}/menus/{menuId}/photo")
	ResponseEntity<Void> deleteMenuPhoto(Authentication authentication, @PathVariable String recordId,
		@PathVariable String menuId) {
		photos.deleteRecordMenuPhoto(accountContext.requireAccountId(authentication), recordId, menuId);
		return ResponseEntity.noContent().build();
	}
}
