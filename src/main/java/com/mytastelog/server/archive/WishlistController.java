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
import com.mytastelog.server.archive.dto.ArchiveRequests.ConvertWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.UpdateWishlistRequest;
import com.mytastelog.server.archive.dto.ArchiveResponses.ApiSuccess;
import com.mytastelog.server.archive.dto.ArchiveResponses.ConvertWishlistResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.WishlistResponse;
import com.mytastelog.server.photo.PhotoResponse;
import com.mytastelog.server.photo.PhotoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController {
	private final ArchiveService service;
	private final AuthenticatedAccountContext accountContext;
	private final PhotoService photos;

	public WishlistController(ArchiveService service, AuthenticatedAccountContext accountContext, PhotoService photos) {
		this.service = service;
		this.accountContext = accountContext;
		this.photos = photos;
	}

	@PostMapping
	ResponseEntity<ApiSuccess<WishlistResponse>> create(Authentication authentication,
		@Valid @RequestBody CreateWishlistRequest request) {
		var result = service.createWishlist(accountContext.requireAccountId(authentication), request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(new ApiSuccess<>(result.value()));
	}

	@PatchMapping("/{id}")
	ApiSuccess<WishlistResponse> update(Authentication authentication, @PathVariable String id,
		@RequestBody UpdateWishlistRequest request) {
		return new ApiSuccess<>(service.updateWishlist(accountContext.requireAccountId(authentication), id, request));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(Authentication authentication, @PathVariable String id) {
		service.deleteWishlist(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/convert-to-record")
	ResponseEntity<ApiSuccess<ConvertWishlistResponse>> convert(Authentication authentication,
		@PathVariable String id, @Valid @RequestBody ConvertWishlistRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(new ApiSuccess<>(service.convertWishlist(
			accountContext.requireAccountId(authentication), id, request)));
	}

	@PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ApiSuccess<PhotoResponse> uploadPhoto(Authentication authentication, @PathVariable String id,
		@RequestPart("file") MultipartFile file) {
		return new ApiSuccess<>(photos.uploadWishlist(accountContext.requireAccountId(authentication), id, file));
	}

	@GetMapping("/{id}/photo")
	ResponseEntity<byte[]> readPhoto(Authentication authentication, @PathVariable String id) {
		var photo = photos.readWishlist(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
			.cacheControl(CacheControl.noStore()).contentLength(photo.bytes().length).body(photo.bytes());
	}

	@DeleteMapping("/{id}/photo")
	ResponseEntity<Void> deletePhoto(Authentication authentication, @PathVariable String id) {
		photos.deleteWishlistPhoto(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.noContent().build();
	}
}
