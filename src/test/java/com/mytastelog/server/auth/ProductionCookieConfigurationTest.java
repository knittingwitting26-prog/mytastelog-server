package com.mytastelog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:prod-cookie;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.datasource.driver-class-name=org.h2.Driver"
})
@ActiveProfiles("prod")
class ProductionCookieConfigurationTest {
	@Autowired Environment environment;

	@Test
	void productionSessionCookieRequiresHttpsAndAllowsCrossSiteRequests() {
		assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
		assertThat(environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class)).isTrue();
		assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("none");
	}
}
