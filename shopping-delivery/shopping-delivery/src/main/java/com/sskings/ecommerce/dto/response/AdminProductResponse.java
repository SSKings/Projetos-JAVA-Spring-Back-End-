package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.Product;

import java.math.BigDecimal;

public record AdminProductResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        int stockQuantity,
        boolean active
) {
    public static AdminProductResponse from(Product p) {
        return new AdminProductResponse(p.getId(), p.getName(), p.getDescription(),
                p.getImageUrl(), p.getPrice(), p.getStockQuantity(), p.isActive());
    }
}
