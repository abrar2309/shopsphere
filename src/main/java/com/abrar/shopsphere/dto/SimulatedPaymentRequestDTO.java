package com.abrar.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SimulatedPaymentRequestDTO {
    @NotBlank(message = "Payment outcome is required")
    @Pattern(regexp = "SUCCESS|FAILURE", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Payment outcome must be SUCCESS or FAILURE")
    private String outcome;

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 100, message = "Idempotency key must contain 8 to 100 characters")
    private String idempotencyKey;

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
