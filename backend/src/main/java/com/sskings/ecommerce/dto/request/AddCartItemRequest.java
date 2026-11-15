package com.sskings.ecommerce.dto.request;

import com.sskings.ecommerce.service.CartService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull Long productId,
        @Min(1) @Max(value = CartService.MAX_UNITS_PER_ITEM, message = "Maximum of 10 units per product") int quantity
) {}
