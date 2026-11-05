package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Limites espelham DECIMAL(10,2) e VARCHAR(255); imagem só por https (sem javascript:/data:/http). */
public record ProductRequest(
        @NotBlank(message = "Name is required") @Size(max = 150) String name,
        @Size(max = 2000, message = "Description must have at most 2000 characters") String description,
        @Size(max = 255) @Pattern(regexp = "^(https://\\S+)?$", message = "Image URL must start with https://") String imageUrl,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        @DecimalMax(value = "99999999.99", message = "Price is too high")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 2 decimal places") BigDecimal price,
        @NotNull(message = "Stock is required") @Min(value = 0, message = "Stock cannot be negative")
        @Max(value = 1_000_000, message = "Stock is too high") Integer stockQuantity,
        Boolean active
) {}
