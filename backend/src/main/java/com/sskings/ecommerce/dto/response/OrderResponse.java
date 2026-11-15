package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal total,
        LocalDateTime createdAt,
        AddressResponse address,
        List<OrderItemResponse> items,
        PaymentResponse payment,
        LocalDateTime deliveredAt,
        LocalDateTime returnRequestedAt,
        LocalDateTime returnDeadline,
        boolean canCancel,
        boolean canRequestReturn
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getStatus(),
                o.getTotal(),
                o.getCreatedAt(),
                AddressResponse.from(o.getAddress()),
                o.getItems().stream().map(OrderItemResponse::from).toList(),
                PaymentResponse.from(o.getPayment()),
                o.getDeliveredAt(),
                o.getReturnRequestedAt(),
                o.returnDeadline(),
                o.isCancellableByCustomer(),
                o.canRequestReturn(LocalDateTime.now())
        );
    }
}
