
package com.abrar.shopsphere.consumer;

import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.service.EventClaimRecoveryService;
import com.abrar.shopsphere.service.EventClaimService;
import com.abrar.shopsphere.service.EventIdempotencyService;
import com.abrar.shopsphere.service.EventProcessingCompletionService;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true"
)
public class PaymentConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentConsumer.class);

    private final EventIdempotencyService eventIdempotencyService;
    private final EventClaimService eventClaimService;
    private final EventClaimRecoveryService eventClaimRecoveryService;
    private final EventProcessingCompletionService
            eventProcessingCompletionService;

    @Value("${shopsphere.kafka.payment.failure-mode:NONE}")
    private String failureMode;

    public PaymentConsumer(
            EventIdempotencyService eventIdempotencyService,
            EventClaimService eventClaimService,
            EventClaimRecoveryService eventClaimRecoveryService,
            EventProcessingCompletionService eventProcessingCompletionService
    ) {
        this.eventIdempotencyService = eventIdempotencyService;
        this.eventClaimService = eventClaimService;
        this.eventClaimRecoveryService = eventClaimRecoveryService;
        this.eventProcessingCompletionService =
                eventProcessingCompletionService;
    }

    @KafkaListener(
            topics = "order-created",
            groupId = "payment-group",
            concurrency = "3"
    )
    public void consumeOrderCreated(
            OrderCreatedEvent event,
            ConsumerRecord<String, OrderCreatedEvent> record
    ) {
        String previousCorrelationId = MDC.get("correlationId");

        try {
            if (event.getCorrelationId() != null) {
                MDC.put("correlationId", event.getCorrelationId());
            }

            UUID eventId = event.getEventId();

            log.info(
                    "Payment consumer received event. " +
                            "eventId={}, correlationId={}, orderId={}, " +
                            "topic={}, partition={}, offset={}, key={}",
                    eventId,
                    event.getCorrelationId(),
                    event.getOrderId(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key()
            );

            Optional<UUID> claimToken =
                    eventClaimService.tryClaimWithToken(eventId);

            if (claimToken.isEmpty()) {
                claimToken =
                        eventClaimRecoveryService.tryRecoverWithToken(eventId);
            }

            if (claimToken.isEmpty()) {
                if (eventIdempotencyService.alreadyProcessed(eventId)) {
                    log.info(
                            "Duplicate event already processed. " +
                                    "Ignoring eventId={}, orderId={}",
                            eventId,
                            event.getOrderId()
                    );
                    return;
                }

                throw new IllegalStateException(
                        "Could not acquire event claim; another worker " +
                                "may own an active claim. eventId=" + eventId
                );
            }

            UUID currentClaimToken = claimToken.get();

            try {
                if (eventIdempotencyService.alreadyProcessed(eventId)) {
                    log.info(
                            "Event was previously processed. " +
                                    "Completing claim. eventId={}",
                            eventId
                    );

                    boolean completed = eventClaimService.markAsCompleted(
                            eventId,
                            currentClaimToken
                    );

                    if (!completed) {
                        throw new IllegalStateException(
                                "Claim ownership changed while completing " +
                                        "previously processed event. eventId="
                                        + eventId
                        );
                    }

                    return;
                }

                log.info(
                        "Processing payment. orderId={}, amount={}",
                        event.getOrderId(),
                        event.getTotalAmount()
                );

                simulateFailureIfConfigured();

                /*
                 * This example simulates payment processing.
                 * A real payment provider must also receive eventId
                 * as its idempotency key.
                 */
                log.info(
                        "Payment processing successful. orderId={}, amount={}",
                        event.getOrderId(),
                        event.getTotalAmount()
                );

                eventProcessingCompletionService.completeProcessing(
                        eventId,
                        currentClaimToken
                );

                log.info(
                        "Payment event processing completed. " +
                                "eventId={}, orderId={}",
                        eventId,
                        event.getOrderId()
                );

            } catch (RuntimeException exception) {
                boolean markedFailed = eventClaimService.markAsFailed(
                        eventId,
                        currentClaimToken
                );

                if (!markedFailed) {
                    log.warn(
                            "Could not mark claim as failed; ownership " +
                                    "may have changed. eventId={}",
                            eventId
                    );
                }

                throw exception;
            }

        } finally {
            if (previousCorrelationId == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previousCorrelationId);
            }
        }
    }

    private void simulateFailureIfConfigured() {
        if (failureMode == null) {
            return;
        }

        switch (failureMode.toUpperCase()) {
            case "PAYMENT":
                throw new RuntimeException(
                        "Simulated payment service failure"
                );

            case "DATABASE":
                throw new RuntimeException(
                        "Simulated database failure"
                );

            case "POISON":
                throw new IllegalArgumentException(
                        "Simulated poison message"
                );

            case "NONE":
                return;

            default:
                log.warn("Unknown failure mode: {}", failureMode);
        }
    }
}
