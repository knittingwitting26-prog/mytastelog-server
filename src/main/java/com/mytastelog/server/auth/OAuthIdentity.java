package com.mytastelog.server.auth;

import com.mytastelog.server.account.AuthProvider;

public record OAuthIdentity(AuthProvider provider, String providerSubject) {
	public OAuthIdentity {
		if (provider == null || providerSubject == null || providerSubject.isBlank()) {
			throw new IllegalArgumentException("OAuth identity requires provider and subject");
		}
	}
}
