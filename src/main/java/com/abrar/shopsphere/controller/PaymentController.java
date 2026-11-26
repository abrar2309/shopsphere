package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.ManualPaymentRequestDTO;
import com.abrar.shopsphere.dto.PaymentResponseDTO;
import com.abrar.shopsphere.dto.PaymentVerificationRequestDTO;
import com.abrar.shopsphere.dto.SimulatedPaymentRequestDTO;
import com.abrar.shopsphere.service.PaymentService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /*
     * CUSTOMER: CREATE UPI OR COD PAYMENT
     */
    @PostMapping("/orders/{orderId}/payment/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponseDTO createManualPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody ManualPaymentRequestDTO request
    ) {
        return paymentService.createManualPayment(orderId, request);
    }

    /*
     * CUSTOMER: SIMULATED PAYMENT FOR TESTING
     */
    @PostMapping("/orders/{orderId}/payment/simulate")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponseDTO simulatePayment(
            @PathVariable Long orderId,
            @Valid @RequestBody SimulatedPaymentRequestDTO request
    ) {
        return paymentService.simulatePayment(orderId, request);
    }

    /*
     * CUSTOMER: GET OWN ORDER PAYMENT
     */
    @GetMapping("/orders/{orderId}/payment")
    public PaymentResponseDTO getPayment(
            @PathVariable Long orderId
    ) {
        return paymentService.getPayment(orderId);
    }

    /*
     * ADMIN: VIEW PENDING UPI AND COD PAYMENTS
     *
     * SecurityConfig already protects /admin/** using ROLE_ADMIN.
     */
    @GetMapping("/admin/payments/pending")
    public List<PaymentResponseDTO> getPendingPayments() {
        return paymentService.getPendingPayments();
    }

    /*
     * ADMIN: APPROVE OR REJECT PAYMENT
     */
    @PostMapping("/admin/payments/{paymentId}/verify")
    public PaymentResponseDTO verifyPayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentVerificationRequestDTO request
    ) {
        return paymentService.verifyPayment(paymentId, request);
    }
}