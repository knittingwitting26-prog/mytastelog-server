package com.mytastelog.server.auth;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import jakarta.servlet.http.HttpServletRequest;

/** Lets an unavailable provider continue to the safe frontend-failure controller. */
public class SafeOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
	private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";
	private static final String AUTHORIZATION_PATH_PREFIX = AUTHORIZATION_BASE_URI + "/";
	private final ClientRegistrationRepository registrations;
	private final OAuth2AuthorizationRequestResolver delegate;

	public SafeOAuth2AuthorizationRequestResolver(ClientRegistrationRepository registrations) {
		this.registrations = registrations;
		this.delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, AUTHORIZATION_BASE_URI);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (path.startsWith(AUTHORIZATION_PATH_PREFIX)) {
			String registrationId = path.substring(AUTHORIZATION_PATH_PREFIX.length());
			if (registrationId.contains("/") || registrations.findByRegistrationId(registrationId) == null) {
				return null;
			}
		}
		return delegate.resolve(request);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
		return registrations.findByRegistrationId(clientRegistrationId) == null
			? null
			: delegate.resolve(request, clientRegistrationId);
	}
}
