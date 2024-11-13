package com.sskings.shopping_delivery.doc;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(infoApi());
    }

    public Info infoApi() {
        return new Info()
                .title("Shopping Delivery")
                .description("Exemplo de uma pequena API de aplicação de vendas.")
                .contact(contact());
    }

    public Contact contact() {
        return new Contact()
                .name("Sérgio S dos Reis")
                .email("sergiosantanadosreis@gmail.com")
                .url("https://www.linkedin.com/in/sskings/");
    }
}
