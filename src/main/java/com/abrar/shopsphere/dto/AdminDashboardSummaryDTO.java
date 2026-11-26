package com.abrar.shopsphere.dto;

import java.math.BigDecimal;

public record AdminDashboardSummaryDTO(
        Long totalUsers,
        Long totalCustomers,
        Long totalSellers,
        Long totalProducts,
        Long totalCategories,
        Long totalOrders,
        Long pendingPayments,
        BigDecimal successfulRevenue
) {}