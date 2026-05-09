package com.certservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.junit.jupiter.api.condition.DisabledIfSystemProperty(named = "env", matches = "ci")
@org.junit.jupiter.api.Disabled("Requires a running MySQL — use the integration test suite instead")
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
