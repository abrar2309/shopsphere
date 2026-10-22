package com.abrar.shopsphere.consumer;

import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.service.EventIdempotencyService;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class PaymentConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PaymentConsumer.class
            );


    private final EventIdempotencyService
            eventIdempotencyService;


    @Value(
            "${shopsphere.kafka.payment.failure-mode:NONE}"
    )
    private String failureMode;


    public PaymentConsumer(
            EventIdempotencyService eventIdempotencyService) {

        this.eventIdempotencyService =
                eventIdempotencyService;
    }


    @KafkaListener(
            topics = "order-created",
            groupId = "payment-group",
            concurrency = "3"
    )
    public void consumeOrderCreated(
            OrderCreatedEvent event,
            ConsumerRecord<
                    String,
                    OrderCreatedEvent> record) {


        String previousCorrelationId =
                MDC.get("correlationId");


        try {

            if (event.getCorrelationId() != null) {

                MDC.put(
                        "correlationId",
                        event.getCorrelationId()
                );
            }


            log.info(
                    "Payment consumer received event. " +
                            "eventId={}, correlationId={}, " +
                            "orderId={}, topic={}, partition={}, " +
                            "offset={}, key={}",
                    event.getEventId(),
                    event.getCorrelationId(),
                    event.getOrderId(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key()
            );


            /*
             * Idempotency check.
             */
            if (eventIdempotencyService
                    .alreadyProcessed(
                            event.getEventId()
                    )) {

                log.warn(
                        "Duplicate event detected. " +
                                "Ignoring event. " +
                                "eventId={}, orderId={}",
                        event.getEventId(),
                        event.getOrderId()
                );

                return;
            }


            log.info(
                    "Processing payment. " +
                            "orderId={}, amount={}",
                    event.getOrderId(),
                    event.getTotalAmount()
            );


            /*
             * Testing failure scenarios.
             */
            simulateFailureIfConfigured();


            log.info(
                    "Payment processing successful. " +
                            "orderId={}, amount={}",
                    event.getOrderId(),
                    event.getTotalAmount()
            );


            /*
             * Mark event as processed
             * after successful processing.
             */
            eventIdempotencyService
                    .markAsProcessed(
                            event.getEventId()
                    );


            log.info(
                    "Payment event processing completed. " +
                            "eventId={}, orderId={}",
                    event.getEventId(),
                    event.getOrderId()
            );


        } finally {

            if (previousCorrelationId == null) {

                MDC.remove(
                        "correlationId"
                );

            } else {

                MDC.put(
                        "correlationId",
                        previousCorrelationId
                );
            }
        }
    }


    private void simulateFailureIfConfigured() {

        if (failureMode == null) {
            return;
        }


        switch (
                failureMode.toUpperCase()) {


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

                log.warn(
                        "Unknown failure mode: {}",
                        failureMode
                );
        }
    }
}
