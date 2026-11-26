
package com.abrar.shopsphere.ai.dto;

public record AiProductSearchResponse(
        Long id,
        String name,
        String description,
        Double price,
        Integer stock,
        String category,
        Double similarity
) {
}
