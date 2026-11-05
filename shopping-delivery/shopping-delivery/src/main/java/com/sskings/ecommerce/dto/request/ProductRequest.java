package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Name is required") @Size(max = 150) String name,
        String description,
        @Size(max = 255) String imageUrl,
        @NotNull(message = "Price is required") @DecimalMin(value = "0.01", message = "Price must be greater than zero") BigDecimal price,
        @NotNull(message = "Stock is required") @Min(value = 0, message = "Stock cannot be negative") Integer stockQuantity,
        Boolean active
) {}
