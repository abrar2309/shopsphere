
package com.abrar.shopsphere.ai.dto;

public record EmbeddingIndexResponse(
        int totalProducts,
        int indexedProducts,
        int skippedProducts,
        String model,
        String message
) {
}
