package com.sskings.ecommerce.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartSummaryResponse(
        List<CartItemResponse> items,
        BigDecimal total,
        int itemCount
) {}
