package com.mytastelog.server.auth;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletResponse;

/** Safe fallback used only when no configured OAuth filter handles a provider start URL. */
@Controller
public class OAuthAvailabilityController {
	private final AuthProperties properties;

	public OAuthAvailabilityController(AuthProperties properties) {
		this.properties = properties;
	}

	@GetMapping({"/oauth2/authorization/google", "/oauth2/authorization/kakao",
		"/oauth2/authorization/naver"})
	public void unavailable(HttpServletResponse response) throws IOException {
		String destination = UriComponentsBuilder.fromUriString(properties.frontendFailureUrl())
			.replaceQueryParam("authError", "SERVER_AUTHENTICATION_FAILED")
			.build().encode().toUriString();
		response.sendRedirect(destination);
	}
}
