package com.sskings.ecommerce.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** app.cors.allowed-origins: origens exatas do front-end; "*" só em desenvolvimento. */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
    public boolean allowAny() {
        return allowedOrigins.contains("*");
    }
}
