package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(@NotNull Long addressId) {}
