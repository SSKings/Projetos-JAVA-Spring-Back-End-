package com.sskings.shopping_delivery.integrationtests.swagger;

import com.sskings.shopping_delivery.config.TestConfigs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
class SwaggerIntegrationTest {

	@DisplayName("Deve Mostrar A Página Do Swagger UI")
	@Test
	void deveMostrarAPaginaDoSwaggerUI() {
		var content = given()
				.baseUri("http://localhost:8080/swagger-ui/index.html")
				.port(TestConfigs.SERVER_PORT)
				.when()
					.get()
				.then()
					.statusCode(200)
				.extract()
					.body()
						.asString();

		Assertions.assertTrue(content.contains("Swagger UI"));

	}

}
