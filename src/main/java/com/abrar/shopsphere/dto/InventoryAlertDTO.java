package com.abrar.shopsphere.dto;

public record InventoryAlertDTO(
        Long productId,
        String productName,
        Integer currentStock,
        Integer threshold,
        boolean outOfStock
) {
}