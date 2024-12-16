package com.sskings.shopping_delivery.integrationtests.swagger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
class SwaggerIntegrationTest {

	@Test
	void contextLoads() {
	}

}
