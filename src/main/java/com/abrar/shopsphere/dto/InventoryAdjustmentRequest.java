package com.abrar.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryAdjustmentRequest(

        @NotNull
        Integer quantityDelta,

        @NotBlank
        @Size(max = 80)
        String reason,

        @Size(max = 40)
        String referenceType,

        @Size(max = 100)
        String referenceId,

        @Size(max = 500)
        String note
) {
}