package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.CartItem;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal
) {
    public static CartItemResponse from(CartItem ci) {
        BigDecimal subtotal = ci.getProduct().getPrice()
                .multiply(BigDecimal.valueOf(ci.getQuantity()));
        return new CartItemResponse(
                ci.getId(),
                ci.getProduct().getId(),
                ci.getProduct().getName(),
                ci.getProduct().getImageUrl(),
                ci.getProduct().getPrice(),
                ci.getQuantity(),
                subtotal
        );
    }
}
