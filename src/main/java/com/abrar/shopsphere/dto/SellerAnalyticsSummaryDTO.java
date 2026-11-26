
package com.abrar.shopsphere.dto;

import java.math.BigDecimal;

public record SellerAnalyticsSummaryDTO(
        Long paidOrders,
        Long unitsSold,
        BigDecimal grossSales,
        BigDecimal inventoryValue,
        Long productCount,
        Long lowStockProducts,
        Long outOfStockProducts
) {
}
