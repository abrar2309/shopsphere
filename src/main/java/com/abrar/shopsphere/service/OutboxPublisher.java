package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.OutboxEvent;
import com.abrar.shopsphere.event.OrderCreatedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@ConditionalOnProperty(
        name = "shopsphere.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Service
public class OutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(
                    OutboxPublisher.class
            );

    private final OutboxClaimService outboxClaimService;

    private final OutboxStatusService outboxStatusService;

    private final KafkaTemplate<
            String,
            OrderCreatedEvent> kafkaTemplate;

    private final ObjectMapper objectMapper;

    public OutboxPublisher(
            OutboxClaimService outboxClaimService,
            OutboxStatusService outboxStatusService,
            KafkaTemplate<
                    String,
                    OrderCreatedEvent> kafkaTemplate,
            ObjectMapper objectMapper) {

        this.outboxClaimService =
                outboxClaimService;

        this.outboxStatusService =
                outboxStatusService;

        this.kafkaTemplate =
                kafkaTemplate;

        this.objectMapper =
                objectMapper;
    }

    @Scheduled(
            fixedDelayString =
                    "${shopsphere.outbox.poll-delay-ms:5000}"
    )
    public void publishUnpublishedEvents() {

        UUID claimToken =
                UUID.randomUUID();

        List<OutboxEvent> events =
                outboxClaimService.claimEvents(
                        20,
                        claimToken
                );

        if (events.isEmpty()) {
            return;
        }

        log.info(
                "Claimed {} outbox events. claimToken={}",
                events.size(),
                claimToken
        );

        for (OutboxEvent outboxEvent : events) {

            try {

                OrderCreatedEvent event =
                        objectMapper.readValue(
                                outboxEvent.getPayload(),
                                OrderCreatedEvent.class
                        );

                kafkaTemplate.send(
                        "order-created",
                        event.getOrderId().toString(),
                        event
                ).whenComplete(
                        (result, exception) -> {

                            if (exception != null) {

                                log.error(
                                        "Failed to publish outbox event. " +
                                                "outboxId={}, eventId={}",
                                        outboxEvent.getId(),
                                        outboxEvent.getEventId(),
                                        exception
                                );

                                return;
                            }

                            outboxStatusService
                                    .markAsPublished(
                                            outboxEvent.getId(),
                                            claimToken
                                    );

                            log.info(
                                    "Outbox event published. " +
                                            "outboxId={}, eventId={}",
                                    outboxEvent.getId(),
                                    outboxEvent.getEventId()
                            );
                        }
                );

            } catch (Exception exception) {

                log.error(
                        "Failed to deserialize outbox event. " +
                                "outboxId={}, eventId={}",
                        outboxEvent.getId(),
                        outboxEvent.getEventId(),
                        exception
                );
            }
        }
    }
}