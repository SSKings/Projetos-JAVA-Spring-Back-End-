package com.sskings.ecommerce.dto.request;

import com.sskings.ecommerce.model.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull(message = "Status is required") OrderStatus status) {}
