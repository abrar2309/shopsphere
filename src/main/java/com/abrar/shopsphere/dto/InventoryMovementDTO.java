package com.abrar.shopsphere.dto;

import java.time.OffsetDateTime;

public record InventoryMovementDTO(
        Long id,
        Long productId,
        String productName,
        Integer quantityBefore,
        Integer quantityAfter,
        Integer quantityDelta,
        String reason,
        String referenceType,
        String referenceId,
        String note,
        OffsetDateTime createdAt
) {
}