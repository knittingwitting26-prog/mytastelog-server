package com.mytastelog.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"naver.local.client-id=test-client-id",
	"naver.local.client-secret=test-client-secret"
})
class MytastelogServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
