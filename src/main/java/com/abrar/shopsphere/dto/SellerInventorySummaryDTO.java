package com.abrar.shopsphere.dto;

import java.math.BigDecimal;

public record SellerInventorySummaryDTO(
        Long sellerId,
        Long productCount,
        Long totalUnits,
        BigDecimal inventoryValue,
        Long outOfStockProducts,
        Long lowStockProducts
) {
}