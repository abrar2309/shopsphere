package com.abrar.shopsphere.dto;

import java.math.BigDecimal;
import java.util.List;

public class CartResponseDTO {

    private Long cartId;
    private List<CartItemResponseDTO> items;
    private BigDecimal totalAmount;

    public CartResponseDTO() {
    }

    public CartResponseDTO(
            Long cartId,
            List<CartItemResponseDTO> items,
            BigDecimal totalAmount) {

        this.cartId = cartId;
        this.items = items;
        this.totalAmount = totalAmount;
    }

    public Long getCartId() {
        return cartId;
    }

    public List<CartItemResponseDTO> getItems() {
        return items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}