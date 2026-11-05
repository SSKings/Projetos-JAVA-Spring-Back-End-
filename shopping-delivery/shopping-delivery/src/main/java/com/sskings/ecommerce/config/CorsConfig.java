package com.sskings.ecommerce.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Todos os métodos usados pela API. PATCH é exigido pela área administrativa
     * (status do pedido e bloqueio/papel de usuário): sem ele o navegador recusa o preflight
     * e o front-end não recebe resposta nenhuma.
     */
    static final String[] ALLOWED_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"};

    private final CorsProperties cors;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsRegistration mapping = registry.addMapping("/**");
        if (cors.allowAny()) {
            mapping.allowedOriginPatterns("*");          // desenvolvimento
        } else {
            mapping.allowedOrigins(cors.allowedOrigins().toArray(String[]::new));
        }
        mapping.allowedMethods(ALLOWED_METHODS)
                .allowedHeaders("Authorization", "Content-Type", "X-Pix-Timestamp", "X-Pix-Signature")
                .exposedHeaders("Retry-After")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
