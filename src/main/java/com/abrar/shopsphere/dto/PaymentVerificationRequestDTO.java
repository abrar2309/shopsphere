package com.abrar.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PaymentVerificationRequestDTO {

    @NotBlank(message = "Decision is required")
    @Pattern(
            regexp = "SUCCESS|FAILURE",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Decision must be SUCCESS or FAILURE"
    )
    private String decision;

    @Size(max = 500)
    private String note;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}