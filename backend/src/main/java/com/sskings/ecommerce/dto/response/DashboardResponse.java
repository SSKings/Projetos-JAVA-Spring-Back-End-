package com.sskings.ecommerce.dto.response;

import java.math.BigDecimal;

public record DashboardResponse(
        long totalProducts,
        long activeProducts,
        long lowStockProducts,
        long totalCustomers,
        long totalOrders,
        long pendingOrders,
        BigDecimal revenue
) {}
