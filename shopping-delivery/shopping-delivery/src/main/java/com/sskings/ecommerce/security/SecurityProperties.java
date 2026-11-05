package com.sskings.ecommerce.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** security.* — ver application.yml. */
@Validated
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @NotBlank String encryptionKey,
        boolean requireAdminMfa,
        @Valid @NotNull Login login,
        @Valid @NotNull RateLimit rateLimit
) {
    public record Jwt(@NotBlank String secret, @Min(1) long expirationMinutes) {}

    public record Login(@Min(1) int maxFailedAttempts, @Min(1) int lockMinutes) {}

    public record RateLimit(boolean enabled, @Min(1) int authPerMinute, @Min(1) int webhookPerMinute) {}
}
