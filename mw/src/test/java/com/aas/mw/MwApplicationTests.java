package com.aas.mw;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=12345678901234567890123456789012")
class MwApplicationTests {

	@Test
	void contextLoads() {
	}

}
