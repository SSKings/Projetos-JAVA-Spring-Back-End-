package com.sskings.shopping_delivery.integrationtests.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sskings.shopping_delivery.config.TestConfigs;
import com.sskings.shopping_delivery.integrationtests.testcontainers.AbstractIntegrationTest;
import com.sskings.shopping_delivery.models.ClienteModel;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;

@ActiveProfiles(value = "integration-test")
@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ClienteControllerIntegrationTest extends AbstractIntegrationTest {

    private static RequestSpecification specification;
    private static ObjectMapper objectMapper;
    private static ClienteModel clienteModel;

    @BeforeAll
    public static void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        specification = new RequestSpecBuilder()
                .setBasePath("/clientes")
                .setPort(TestConfigs.SERVER_PORT)
                .addFilter(new RequestLoggingFilter(LogDetail.ALL))
                .addFilter(new ResponseLoggingFilter(LogDetail.ALL))
                .build();

        clienteModel = new ClienteModel();
        clienteModel.setNome("Cliente Teste");
        clienteModel.setEmail("test@email.com");
        clienteModel.setTelefone("123456789");
        clienteModel.setCpf("123.456.789-00");
        clienteModel.setDataCadastro(LocalDateTime.of(2020, 10, 10, 10, 10));

    }

    @DisplayName("Dado Cliente Deve Salvar E Retornar O Cliente")
    @Test
    @Order(1)
    void dadoClienteDeveSalvarERetornarOCliente() throws JsonProcessingException {
        var content = given().spec(specification)
                .contentType(TestConfigs.CONTENT_TYPE_JSON)
                .body(clienteModel)
                .when()
                .post()
                .then()
                .statusCode(201)
                .extract()
                .body()
                .asString();

        ClienteModel clienteCriado =  objectMapper.readValue(content, ClienteModel.class);
        clienteModel = clienteCriado;

        Assertions.assertNotNull(clienteCriado.getId());
        Assertions.assertNotNull(clienteCriado.getNome());
        Assertions.assertNotNull(clienteCriado.getEmail());
        Assertions.assertNotNull(clienteCriado.getTelefone());
        Assertions.assertNotNull(clienteCriado.getCpf());
        Assertions.assertNotNull(clienteCriado.getDataCadastro());

        Assertions.assertTrue(clienteCriado.getId() > 0);
        Assertions.assertEquals("Cliente Teste", clienteCriado.getNome());
        Assertions.assertEquals("test@email.com", clienteCriado.getEmail());
        Assertions.assertEquals("123456789", clienteCriado.getTelefone());
        Assertions.assertEquals("123.456.789-00", clienteCriado.getCpf());
        Assertions.assertEquals(LocalDateTime.of(2020, 10, 10, 10, 10),
                clienteCriado.getDataCadastro());
    }

}
