package com.mytastelog.server.archive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mytastelog.server.account.AuthenticatedAccountContext;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ArchiveImportRequest;
import com.mytastelog.server.archive.dto.ArchiveImportContract.ArchiveImportResponse;
import com.mytastelog.server.archive.dto.ArchiveResponses.ApiSuccess;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/archive/imports")
public class ArchiveImportController {
	private final ArchiveImportService service;
	private final AuthenticatedAccountContext accountContext;

	public ArchiveImportController(ArchiveImportService service, AuthenticatedAccountContext accountContext) {
		this.service = service;
		this.accountContext = accountContext;
	}

	@PostMapping
	ResponseEntity<ApiSuccess<ArchiveImportResponse>> importArchive(Authentication authentication,
		@Valid @RequestBody ArchiveImportRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(new ApiSuccess<>(
			service.importArchive(accountContext.requireAccountId(authentication), request)));
	}
}
