package com.mytastelog.server.auth;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuthAuthenticationFailureHandler implements AuthenticationFailureHandler {
	private final AuthProperties properties;

	public OAuthAuthenticationFailureHandler(AuthProperties properties) {
		this.properties = properties;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException exception) throws IOException, ServletException {
		String destination = UriComponentsBuilder.fromUriString(properties.frontendFailureUrl())
			.replaceQueryParam("authError", safeCode(exception))
			.build().encode().toUriString();
		response.sendRedirect(destination);
	}

	private String safeCode(AuthenticationException exception) {
		if (exception instanceof AccountResolutionAuthenticationException) return "ACCOUNT_RESOLUTION_FAILED";
		if (exception instanceof OAuth2AuthenticationException oauth) {
			String code = oauth.getError().getErrorCode();
			if ("access_denied".equals(code)) return "PROVIDER_CANCELLED";
			if ("invalid_authentication_response".equals(code)) return "INVALID_AUTHENTICATION_RESPONSE";
		}
		return "PROVIDER_AUTHENTICATION_FAILED";
	}
}
