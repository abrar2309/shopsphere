package com.abrar.shopsphere.dto;

import java.time.OffsetDateTime;

public record AdminPaymentHistoryDTO(
        Long id,
        Long paymentId,
        Long orderId,
        String oldStatus,
        String newStatus,
        String paymentMethod,
        String adminNote,
        String verifiedBy,
        OffsetDateTime verifiedAt,
        OffsetDateTime changedAt
) {}