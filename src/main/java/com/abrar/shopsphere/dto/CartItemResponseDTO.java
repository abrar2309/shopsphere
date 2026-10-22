package com.abrar.shopsphere.dto;

import java.math.BigDecimal;

public class CartItemResponseDTO {

    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;

    public CartItemResponseDTO() {
    }

    public CartItemResponseDTO(
            Long productId,
            String productName,
            BigDecimal price,
            Integer quantity,
            BigDecimal subtotal) {

        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}