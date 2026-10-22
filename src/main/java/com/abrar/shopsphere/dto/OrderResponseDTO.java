package com.abrar.shopsphere.dto;

import java.util.List;

public class OrderResponseDTO {

    private Long orderId;

    private Double totalAmount;

    private String status;

    private List<OrderItemResponseDTO> items;

    public OrderResponseDTO(
            Long orderId,
            Double totalAmount,
            String status,
            List<OrderItemResponseDTO> items) {

        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.items = items;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public List<OrderItemResponseDTO> getItems() {
        return items;
    }
}