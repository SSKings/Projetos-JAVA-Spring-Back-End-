package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static OrderItemResponse from(OrderItem oi) {
        return new OrderItemResponse(oi.getId(), oi.getProductName(),
                oi.getQuantity(), oi.getUnitPrice(), oi.getSubtotal());
    }
}
