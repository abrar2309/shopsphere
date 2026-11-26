package com.abrar.shopsphere.dto;

public record AdminOrderDTO(
        Long orderId,
        Long customerId,
        String customerName,
        String customerEmail,
        Double totalAmount,
        String orderStatus,
        Long itemCount,
        String paymentStatus,
        String paymentMethod
) {}