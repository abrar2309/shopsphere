package com.abrar.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ManualPaymentRequestDTO {

    @NotBlank(message = "Payment method is required")
    @Pattern(
            regexp = "UPI|COD",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Payment method must be UPI or COD"
    )
    private String paymentMethod;

    @Size(max = 100, message = "UPI transaction ID cannot exceed 100 characters")
    private String upiTransactionId;

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 100)
    private String idempotencyKey;

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getUpiTransactionId() {
        return upiTransactionId;
    }

    public void setUpiTransactionId(String upiTransactionId) {
        this.upiTransactionId = upiTransactionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}