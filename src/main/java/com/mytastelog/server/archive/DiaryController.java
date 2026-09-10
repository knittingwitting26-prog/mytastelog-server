package com.mytastelog.server.archive;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mytastelog.server.account.AuthenticatedAccountContext;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.ReplaceCollectionOrderRequest;
import com.mytastelog.server.archive.dto.ArchiveRequests.UpdateDiaryRequest;
import com.mytastelog.server.archive.dto.ArchiveResponses.ApiSuccess;
import com.mytastelog.server.archive.dto.ArchiveResponses.CollectionResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.DiaryResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/diaries")
public class DiaryController {
	private final ArchiveService service;
	private final AuthenticatedAccountContext accountContext;

	public DiaryController(ArchiveService service, AuthenticatedAccountContext accountContext) {
		this.service = service;
		this.accountContext = accountContext;
	}

	@GetMapping
	ApiSuccess<List<DiaryResponse>> list(Authentication authentication) {
		return new ApiSuccess<>(service.listDiaries(accountContext.requireAccountId(authentication)));
	}

	@PostMapping
	ResponseEntity<ApiSuccess<DiaryResponse>> create(Authentication authentication,
		@Valid @RequestBody CreateDiaryRequest request) {
		var result = service.createDiary(accountContext.requireAccountId(authentication), request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(new ApiSuccess<>(result.value()));
	}

	@PatchMapping("/{id}")
	ApiSuccess<DiaryResponse> update(Authentication authentication, @PathVariable String id,
		@RequestBody UpdateDiaryRequest request) {
		return new ApiSuccess<>(service.updateDiary(accountContext.requireAccountId(authentication), id, request));
	}

	@PutMapping("/{diaryId}/collection-order")
	ApiSuccess<List<CollectionResponse>> replaceCollectionOrder(Authentication authentication,
		@PathVariable String diaryId, @Valid @RequestBody ReplaceCollectionOrderRequest request) {
		return new ApiSuccess<>(service.replaceCollectionOrder(
			accountContext.requireAccountId(authentication), diaryId, request));
	}
}
