package com.mytastelog.server.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/** Adds the state parameter required by Naver's token endpoint contract. */
@Component
public class NaverAuthorizationCodeTokenRequestParametersConverter implements
	Converter<OAuth2AuthorizationCodeGrantRequest, MultiValueMap<String, String>> {
	@Override
	public MultiValueMap<String, String> convert(OAuth2AuthorizationCodeGrantRequest request) {
		MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
		if (!"naver".equals(request.getClientRegistration().getRegistrationId())) {
			return parameters;
		}
		String state = request.getAuthorizationExchange().getAuthorizationRequest().getState();
		if (StringUtils.hasText(state)) {
			parameters.add("state", state);
		}
		return parameters;
	}
}
