package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder_Id(Long orderId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByStatusAndPaymentMethodInOrderByCreatedAtAsc(
            String status,
            List<String> paymentMethods
    );
}