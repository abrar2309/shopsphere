package com.abrar.shopsphere.dto;

import java.time.OffsetDateTime;

public record WishlistItemDTO(
        Long id,
        Long productId,
        String name,
        String description,
        Double price,
        Integer stock,
        String imageUrl,
        String category,
        OffsetDateTime createdAt
) {
}