
package com.abrar.shopsphere.ai.dto;

public record PersonalizedRecommendationDTO(
        Long id,
        String name,
        String description,
        Double price,
        Integer stock,
        String category,
        String imageUrl,
        Double score,
        String reason
) {
}
