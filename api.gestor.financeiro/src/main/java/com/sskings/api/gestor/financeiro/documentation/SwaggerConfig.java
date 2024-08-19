package com.sskings.api.gestor.financeiro.documentation;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    String schemeName = "bearerAuth";
    String bearerFormat = "JWT";
    String scheme = "bearer";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement()
                        .addList(schemeName)).components(new Components()
                        .addSecuritySchemes(
                                schemeName, new SecurityScheme()
                                        .name(schemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .bearerFormat(bearerFormat)
                                        .in(SecurityScheme.In.HEADER)
                                        .scheme(scheme)
                        )
                )
                .info(apiInfo());
    }

    public Info apiInfo(){
        return new Info()
                .title("Gestor Financeiro - Rest API")
                .description("Exemplo de API RESTful e conhecimentos do Framework Spring")
                .version("1.0")
                .contact(contact());
    }

    public Contact contact(){
        return new Contact()
                .name("Sérgio Santana dos Reis")
                .email("sergiosantanadosreis@gmail.com")
                .url("https://www.linkedin.com/in/sskings/");
    }
}