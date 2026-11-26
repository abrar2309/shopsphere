
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.OutboxEvent;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
            LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimService outboxClaimService;
    private final OutboxStatusService outboxStatusService;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public OutboxPublisher(
            OutboxClaimService outboxClaimService,
            OutboxStatusService outboxStatusService,
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate) {

        this.outboxClaimService = outboxClaimService;
        this.outboxStatusService = outboxStatusService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(
            fixedDelayString =
                    "${shopsphere.outbox.poll-delay-ms:5000}"
    )
    public void publishUnpublishedEvents() {

        UUID claimToken = UUID.randomUUID();

        List<OutboxEvent> events =
                outboxClaimService.claimEvents(20, claimToken);

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

                String eventType = outboxEvent.getEventType();

                if ("ProductCacheInvalidation".equals(eventType)) {

                    processProductCacheInvalidation(
                            outboxEvent,
                            claimToken
                    );

                } else if ("OrderCreated".equals(eventType)) {

                    processOrderCreatedEvent(
                            outboxEvent,
                            claimToken
                    );

                } else {

                    log.error(
                            "Unsupported outbox event type. " +
                                    "outboxId={}, eventType={}",
                            outboxEvent.getId(),
                            eventType
                    );
                }

            } catch (Exception exception) {

                log.error(
                        "Failed to process outbox event. " +
                                "outboxId={}, eventId={}, eventType={}",
                        outboxEvent.getId(),
                        outboxEvent.getEventId(),
                        outboxEvent.getEventType(),
                        exception
                );
            }
        }
    }

    private void processProductCacheInvalidation(
            OutboxEvent outboxEvent,
            UUID claimToken) throws Exception {

        JsonNode payload =
                objectMapper.readTree(outboxEvent.getPayload());

        JsonNode productIdNode = payload.get("productId");

        if (productIdNode == null
                || !productIdNode.canConvertToLong()
                || productIdNode.asLong() <= 0) {

            throw new IllegalArgumentException(
                    "Invalid productId in cache invalidation event"
            );
        }

        Long productId = productIdNode.asLong();

        String cacheKey = "product:v2:" + productId;

        // Redis DEL is idempotent.
        // Deleting a missing key is also considered successful.
        redisTemplate.delete(cacheKey);

        // Mark published only after Redis deletion succeeds.
        outboxStatusService.markAsPublished(
                outboxEvent.getId(),
                claimToken
        );

        log.info(
                "Product cache invalidated. productId={}, outboxId={}",
                productId,
                outboxEvent.getId()
        );
    }

    private void processOrderCreatedEvent(
            OutboxEvent outboxEvent,
            UUID claimToken) throws Exception {

        OrderCreatedEvent event =
                objectMapper.readValue(
                        outboxEvent.getPayload(),
                        OrderCreatedEvent.class
                );

        kafkaTemplate.send(
                "order-created",
                event.getOrderId().toString(),
                event
        ).whenComplete((result, exception) -> {

            if (exception != null) {

                log.error(
                        "Failed to publish order event. " +
                                "outboxId={}, eventId={}",
                        outboxEvent.getId(),
                        outboxEvent.getEventId(),
                        exception
                );

                return;
            }

            outboxStatusService.markAsPublished(
                    outboxEvent.getId(),
                    claimToken
            );

            log.info(
                    "Order event published. outboxId={}, eventId={}",
                    outboxEvent.getId(),
                    outboxEvent.getEventId()
            );
        });
    }
}
