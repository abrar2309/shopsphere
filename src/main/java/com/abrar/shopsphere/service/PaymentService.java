package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.ManualPaymentRequestDTO;
import com.abrar.shopsphere.dto.PaymentResponseDTO;
import com.abrar.shopsphere.dto.PaymentVerificationRequestDTO;
import com.abrar.shopsphere.dto.SimulatedPaymentRequestDTO;
import com.abrar.shopsphere.entity.Order;
import com.abrar.shopsphere.entity.OrderItem;
import com.abrar.shopsphere.entity.Payment;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.OrderRepository;
import com.abrar.shopsphere.repository.PaymentRepository;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    /*
     * MANUAL UPI OR COD PAYMENT
     */
    @Transactional
    public PaymentResponseDTO createManualPayment(
            Long orderId,
            ManualPaymentRequestDTO request
    ) {
        User customer = authenticatedCustomer();

        Order order = orderRepository.findByIdAndUserIdForUpdate(
                orderId,
                customer.getId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found."
        ));

        String method = request.getPaymentMethod()
                .trim()
                .toUpperCase();

        String key = request.getIdempotencyKey().trim();

        var existingByKey = paymentRepository.findByIdempotencyKey(key);

        if (existingByKey.isPresent()) {
            Payment existing = existingByKey.get();

            if (!existing.getOrder().getId().equals(orderId)
                    || !existing.getPaymentMethod().equals(method)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This idempotency key was already used."
                );
            }

            return new PaymentResponseDTO(existing);
        }

        if (paymentRepository.findByOrder_Id(orderId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A payment attempt already exists for this order."
            );
        }

        if (!"CREATED".equalsIgnoreCase(order.getStatus())
                && !"PAYMENT_PENDING".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order cannot accept a new payment. Current status: "
                            + order.getStatus()
            );
        }

        String transactionId = null;

        if ("UPI".equals(method)) {
            transactionId = request.getUpiTransactionId();

            if (transactionId == null || transactionId.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Enter your UPI transaction ID after making the payment."
                );
            }

            transactionId = transactionId.trim();

            if (transactionId.length() > 100) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "UPI transaction ID is too long."
                );
            }

        } else if (!"COD".equals(method)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported payment method."
            );
        }

        Payment payment = new Payment();

        payment.setOrder(order);
        payment.setCustomerId(customer.getId());
        payment.setPaymentReference(
                "SHP-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .toUpperCase()
        );
        payment.setIdempotencyKey(key);
        payment.setAmount(BigDecimal.valueOf(order.getTotalAmount()));
        payment.setPaymentMethod(method);
        payment.setStatus("PENDING");

        if ("UPI".equals(method)) {
            payment.setUpiTransactionId(transactionId);
            order.setStatus("PAYMENT_PENDING");
        } else {
            order.setStatus("COD_PENDING");
        }

        return new PaymentResponseDTO(
                paymentRepository.save(payment)
        );
    }

    /*
     * EXISTING SIMULATED PAYMENT
     */
    @Transactional
    public PaymentResponseDTO simulatePayment(
            Long orderId,
            SimulatedPaymentRequestDTO request
    ) {
        User customer = authenticatedCustomer();

        Order order = orderRepository.findByIdAndUserIdForUpdate(
                orderId,
                customer.getId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found."
        ));

        String key = request.getIdempotencyKey().trim();
        String requestedOutcome = request.getOutcome()
                .trim()
                .toUpperCase();

        var byKey = paymentRepository.findByIdempotencyKey(key);

        if (byKey.isPresent()) {
            Payment existing = byKey.get();

            if (!existing.getOrder().getId().equals(orderId)
                    || !existing.getStatus().equals(requestedOutcome)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This idempotency key was already used."
                );
            }

            return new PaymentResponseDTO(existing);
        }

        if (paymentRepository.findByOrder_Id(orderId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A payment attempt already exists for this order."
            );
        }

        if (!"CREATED".equalsIgnoreCase(order.getStatus())
                && !"PAYMENT_PENDING".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order cannot be paid. Current status: "
                            + order.getStatus()
            );
        }

        Payment payment = new Payment();

        payment.setOrder(order);
        payment.setCustomerId(customer.getId());
        payment.setPaymentReference(
                "SHP-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .toUpperCase()
        );
        payment.setIdempotencyKey(key);
        payment.setAmount(BigDecimal.valueOf(order.getTotalAmount()));
        payment.setPaymentMethod("SIMULATED");
        payment.setStatus("PENDING");

        payment = paymentRepository.saveAndFlush(payment);

        if ("SUCCESS".equals(requestedOutcome)) {
            payment.setStatus("SUCCESS");
            order.setStatus("PAID");
        } else {
            restoreReservedInventory(order.getItems());

            payment.setStatus("FAILURE");
            payment.setFailureReason("Simulated payment was declined.");
            order.setStatus("PAYMENT_FAILED");
        }

        return new PaymentResponseDTO(
                paymentRepository.save(payment)
        );
    }

    /*
     * CUSTOMER PAYMENT STATUS
     */
    @Transactional(readOnly = true)
    public PaymentResponseDTO getPayment(Long orderId) {
        User customer = authenticatedCustomer();

        orderRepository.findByIdAndUserId(
                orderId,
                customer.getId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found."
        ));

        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No payment attempt exists for this order."
                ));

        return new PaymentResponseDTO(payment);
    }

    /*
     * ADMIN: LIST PENDING UPI AND COD PAYMENTS
     */
    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> getPendingPayments() {

        return paymentRepository
                .findByStatusAndPaymentMethodInOrderByCreatedAtAsc(
                        "PENDING",
                        List.of("UPI", "COD")
                )
                .stream()
                .map(PaymentResponseDTO::new)
                .toList();
    }

    /*
     * ADMIN: VERIFY UPI PAYMENT OR COD COLLECTION
     */
    @Transactional
    public PaymentResponseDTO verifyPayment(
            Long paymentId,
            PaymentVerificationRequestDTO request
    ) {
        User admin = authenticatedCustomer();

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found."
                ));

        Order order = orderRepository.findByIdAndUserIdForUpdate(
                payment.getOrder().getId(),
                payment.getCustomerId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Associated order not found."
        ));

        if (!"PENDING".equals(payment.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending payments can be verified."
            );
        }

        String decision = request.getDecision()
                .trim()
                .toUpperCase();

        if (!"UPI".equals(payment.getPaymentMethod())
                && !"COD".equals(payment.getPaymentMethod())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only UPI and COD payments can be manually verified."
            );
        }

        payment.setAdminNote(request.getNote());
        payment.setVerifiedAt(LocalDateTime.now());
        payment.setVerifiedBy(admin.getId());

        if ("SUCCESS".equals(decision)) {

            payment.setStatus("SUCCESS");
            payment.setFailureReason(null);
            order.setStatus("CONFIRMED");

        } else {

            restoreReservedInventory(order.getItems());

            payment.setStatus("FAILURE");
            payment.setFailureReason(
                    request.getNote() == null || request.getNote().isBlank()
                            ? "Payment was rejected by administrator."
                            : request.getNote()
            );

            order.setStatus("PAYMENT_FAILED");
        }

        return new PaymentResponseDTO(
                paymentRepository.save(payment)
        );
    }

    /*
     * RELEASE RESERVED INVENTORY AFTER A VERIFIED FAILURE
     */
    private void restoreReservedInventory(List<OrderItem> items) {

        List<OrderItem> orderedItems = items.stream()
                .sorted(Comparator.comparing(
                        item -> item.getProduct().getId()
                ))
                .toList();

        for (OrderItem item : orderedItems) {

            Product product = productRepository
                    .findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Product " + item.getProduct().getId()
                                    + " no longer exists."
                    ));

            product.setStock(
                    Math.addExact(
                            product.getStock(),
                            item.getQuantity()
                    )
            );
        }
    }

    /*
     * RESOLVE AUTHENTICATED USER
     */
    private User authenticatedCustomer() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new AccessDeniedException(
                    "Authentication is required."
            );
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found."
                ));
    }
}
