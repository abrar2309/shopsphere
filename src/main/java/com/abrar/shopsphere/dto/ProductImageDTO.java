
package com.abrar.shopsphere.dto;

import java.time.OffsetDateTime;

public record ProductImageDTO(
        Long id,
        Long productId,
        String imageUrl,
        Integer displayOrder,
        Boolean primary,
        OffsetDateTime createdAt
) {
}
