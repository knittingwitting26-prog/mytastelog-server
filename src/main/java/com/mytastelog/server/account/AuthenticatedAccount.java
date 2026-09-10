package com.mytastelog.server.account;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Internal principal. It deliberately contains no provider token or raw profile. */
public record AuthenticatedAccount(String accountId) implements OAuth2User, Serializable {
	@Override
	public Map<String, Object> getAttributes() {
		return Map.of("accountId", accountId);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public String getName() {
		return accountId;
	}
}
