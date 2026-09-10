package com.mytastelog.server.auth;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.mytastelog.server.account.AuthenticatedAccount;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuthAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
	private final OAuthIdentityResolver identityResolver;
	private final AuthenticationAccountService accountService;
	private final OAuthAuthenticationFailureHandler failureHandler;
	private final AuthProperties properties;
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public OAuthAuthenticationSuccessHandler(OAuthIdentityResolver identityResolver,
		AuthenticationAccountService accountService, OAuthAuthenticationFailureHandler failureHandler,
		AuthProperties properties) {
		this.identityResolver = identityResolver;
		this.accountService = accountService;
		this.failureHandler = failureHandler;
		this.properties = properties;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
		Authentication authentication) throws IOException, ServletException {
		try {
			OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
			AuthenticatedAccount principal = accountService.resolve(identityResolver.resolve(oauth));
			OAuth2AuthenticationToken internal = new OAuth2AuthenticationToken(
				principal, principal.getAuthorities(), oauth.getAuthorizedClientRegistrationId());
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(internal);
			SecurityContextHolder.setContext(context);
			securityContextRepository.saveContext(context, request, response);
			response.sendRedirect(properties.frontendSuccessUrl());
		} catch (AuthenticationException failure) {
			clearFailedAuthentication(request);
			failureHandler.onAuthenticationFailure(request, response, failure);
		} catch (RuntimeException failure) {
			clearFailedAuthentication(request);
			failureHandler.onAuthenticationFailure(request, response,
				new AccountResolutionAuthenticationException());
		}
	}

	private void clearFailedAuthentication(HttpServletRequest request) {
			SecurityContext empty = SecurityContextHolder.createEmptyContext();
			SecurityContextHolder.setContext(empty);
			if (request.getSession(false) != null) request.getSession(false).invalidate();
		}
}
