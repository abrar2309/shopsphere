package com.abrar.shopsphere.dto;

import com.abrar.shopsphere.entity.ProductVariant;

public record ProductVariantResponseDTO(
        Long id, Long productId, String sku, String size, String color,
        Double price, Integer stock, boolean active) {
    public static ProductVariantResponseDTO from(ProductVariant v) {
        return new ProductVariantResponseDTO(
                v.getId(), v.getProduct().getId(), v.getSku(), v.getSize(),
                v.getColor(), v.getPrice(), v.getStock(), v.isActive());
    }
}
