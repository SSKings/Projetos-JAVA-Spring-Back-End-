package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.Product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        int stockQuantity
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(),
                p.getImageUrl(), p.getPrice(), p.getStockQuantity());
    }
}
