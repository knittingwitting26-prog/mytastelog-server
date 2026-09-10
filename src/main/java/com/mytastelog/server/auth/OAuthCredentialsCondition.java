package com.mytastelog.server.auth;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class OAuthCredentialsCondition implements Condition {
	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		return complete(context, "google") || complete(context, "kakao") || complete(context, "naver");
	}

	private boolean complete(ConditionContext context, String provider) {
		String prefix = "app.auth." + provider;
		return StringUtils.hasText(context.getEnvironment().getProperty(prefix + ".client-id"))
			&& StringUtils.hasText(context.getEnvironment().getProperty(prefix + ".client-secret"));
	}
}
