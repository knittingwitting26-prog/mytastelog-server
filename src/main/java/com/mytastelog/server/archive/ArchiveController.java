package com.mytastelog.server.archive;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mytastelog.server.account.AuthenticatedAccountContext;
import com.mytastelog.server.archive.dto.ArchiveResponses.ApiSuccess;
import com.mytastelog.server.archive.dto.ArchiveResponses.ArchiveResponse;

@RestController
@RequestMapping("/api/v1/archive")
public class ArchiveController {
	private final ArchiveService service;
	private final AuthenticatedAccountContext accountContext;

	public ArchiveController(ArchiveService service, AuthenticatedAccountContext accountContext) {
		this.service = service;
		this.accountContext = accountContext;
	}

	@GetMapping
	ApiSuccess<ArchiveResponse> load(Authentication authentication) {
		return new ApiSuccess<>(service.loadArchive(accountContext.requireAccountId(authentication)));
	}
}
