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
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.UpdateCollectionRequest;
import com.mytastelog.server.archive.dto.ArchiveResponses.ApiSuccess;
import com.mytastelog.server.archive.dto.ArchiveResponses.CollectionResponse;
import com.mytastelog.server.photo.PhotoResponse;
import com.mytastelog.server.photo.PhotoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {
	private final ArchiveService service;
	private final AuthenticatedAccountContext accountContext;
	private final PhotoService photos;

	public CollectionController(ArchiveService service, AuthenticatedAccountContext accountContext, PhotoService photos) {
		this.service = service;
		this.accountContext = accountContext;
		this.photos = photos;
	}

	@PostMapping
	ResponseEntity<ApiSuccess<CollectionResponse>> create(Authentication authentication,
		@Valid @RequestBody CreateCollectionRequest request) {
		var result = service.createCollection(accountContext.requireAccountId(authentication), request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(new ApiSuccess<>(result.value()));
	}

	@PatchMapping("/{id}")
	ApiSuccess<CollectionResponse> update(Authentication authentication, @PathVariable String id,
		@RequestBody UpdateCollectionRequest request) {
		return new ApiSuccess<>(service.updateCollection(accountContext.requireAccountId(authentication), id, request));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(Authentication authentication, @PathVariable String id) {
		service.deleteCollection(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{collectionId}/items/{itemId}")
	ApiSuccess<CollectionResponse> addItem(Authentication authentication, @PathVariable String collectionId,
		@PathVariable String itemId) {
		return new ApiSuccess<>(service.addCollectionItem(accountContext.requireAccountId(authentication),
			collectionId, itemId));
	}

	@DeleteMapping("/{collectionId}/items/{itemId}")
	ResponseEntity<Void> removeItem(Authentication authentication, @PathVariable String collectionId,
		@PathVariable String itemId) {
		service.removeCollectionItem(accountContext.requireAccountId(authentication), collectionId, itemId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ApiSuccess<PhotoResponse> uploadPhoto(Authentication authentication, @PathVariable String id,
		@RequestPart("file") MultipartFile file) {
		return new ApiSuccess<>(photos.uploadCollection(accountContext.requireAccountId(authentication), id, file));
	}

	@GetMapping("/{id}/photo")
	ResponseEntity<byte[]> readPhoto(Authentication authentication, @PathVariable String id) {
		var photo = photos.readCollection(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
			.cacheControl(CacheControl.noStore()).contentLength(photo.bytes().length).body(photo.bytes());
	}

	@DeleteMapping("/{id}/photo")
	ResponseEntity<Void> deletePhoto(Authentication authentication, @PathVariable String id) {
		photos.deleteCollectionPhoto(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.noContent().build();
	}
}
