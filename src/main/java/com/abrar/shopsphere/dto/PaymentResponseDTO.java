package com.abrar.shopsphere.dto;

import com.abrar.shopsphere.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponseDTO {

    private final Long paymentId;
    private final Long orderId;
    private final String paymentReference;
    private final BigDecimal amount;
    private final String status;
    private final String paymentMethod;
    private final String failureReason;
    private final String upiTransactionId;
    private final String adminNote;
    private final LocalDateTime verifiedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public PaymentResponseDTO(Payment payment) {
        this.paymentId = payment.getId();
        this.orderId = payment.getOrder().getId();
        this.paymentReference = payment.getPaymentReference();
        this.amount = payment.getAmount();
        this.status = payment.getStatus();
        this.paymentMethod = payment.getPaymentMethod();
        this.failureReason = payment.getFailureReason();
        this.upiTransactionId = payment.getUpiTransactionId();
        this.adminNote = payment.getAdminNote();
        this.verifiedAt = payment.getVerifiedAt();
        this.createdAt = payment.getCreatedAt();
        this.updatedAt = payment.getUpdatedAt();
    }

    public Long getPaymentId() { return paymentId; }
    public Long getOrderId() { return orderId; }
    public String getPaymentReference() { return paymentReference; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getFailureReason() { return failureReason; }
    public String getUpiTransactionId() { return upiTransactionId; }
    public String getAdminNote() { return adminNote; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}