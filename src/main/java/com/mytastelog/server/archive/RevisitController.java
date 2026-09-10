package com.mytastelog.server.archive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mytastelog.server.account.AuthenticatedAccountContext;
import com.mytastelog.server.archive.dto.ArchiveRequests.CreateRevisitIntentRequest;
import com.mytastelog.server.archive.dto.ArchiveResponses.ApiSuccess;
import com.mytastelog.server.archive.dto.ArchiveResponses.RevisitIntentResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/revisit")
public class RevisitController {
	private final ArchiveService service;
	private final AuthenticatedAccountContext accountContext;
	public RevisitController(ArchiveService service, AuthenticatedAccountContext accountContext) { this.service = service; this.accountContext = accountContext; }
	@PostMapping ResponseEntity<ApiSuccess<RevisitIntentResponse>> create(Authentication authentication, @Valid @RequestBody CreateRevisitIntentRequest request) {
		var result = service.createRevisitIntent(accountContext.requireAccountId(authentication), request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(new ApiSuccess<>(result.value()));
	}
	@DeleteMapping("/{id}") ResponseEntity<Void> delete(Authentication authentication, @PathVariable String id) {
		service.deleteRevisitIntent(accountContext.requireAccountId(authentication), id);
		return ResponseEntity.noContent().build();
	}
}
