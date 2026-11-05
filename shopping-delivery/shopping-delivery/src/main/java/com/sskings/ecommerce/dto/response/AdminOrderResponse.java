package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal total,
        LocalDateTime createdAt,
        Long customerId,
        String customerName,
        String customerEmail,
        AddressResponse address,
        List<OrderItemResponse> items
) {
    public static AdminOrderResponse from(Order o) {
        return new AdminOrderResponse(
                o.getId(), o.getStatus(), o.getTotal(), o.getCreatedAt(),
                o.getUser().getId(), o.getUser().getName(), o.getUser().getEmail(),
                AddressResponse.from(o.getAddress()),
                o.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
