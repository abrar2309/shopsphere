
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.OutboxEvent;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.repository.OutboxEventRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {

        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveOrderCreatedEvent(OrderCreatedEvent event) {

        try {
            String payload =
                    objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent();

            outboxEvent.setEventId(event.getEventId());
            outboxEvent.setEventType("OrderCreated");
            outboxEvent.setAggregateId(
                    event.getOrderId().toString()
            );
            outboxEvent.setPayload(payload);
            outboxEvent.setCreatedAt(LocalDateTime.now());
            outboxEvent.setPublished(false);

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException exception) {

            throw new IllegalStateException(
                    "Failed to serialize OrderCreatedEvent",
                    exception
            );
        }
    }

    @Transactional
    public void saveProductCacheInvalidationEvent(
            Long productId,
            String operation) {

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException(
                    "Product ID must be positive"
            );
        }

        if (!"UPDATED".equals(operation)
                && !"DELETED".equals(operation)) {
            throw new IllegalArgumentException(
                    "Unsupported product operation"
            );
        }

        try {
            String payload =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "productId", productId,
                                    "operation", operation
                            )
                    );

            OutboxEvent outboxEvent = new OutboxEvent();

            outboxEvent.setEventId(UUID.randomUUID());
            outboxEvent.setEventType(
                    "ProductCacheInvalidation"
            );
            outboxEvent.setAggregateId(
                    productId.toString()
            );
            outboxEvent.setPayload(payload);
            outboxEvent.setCreatedAt(LocalDateTime.now());
            outboxEvent.setPublished(false);

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException exception) {

            throw new IllegalStateException(
                    "Failed to serialize ProductCacheInvalidation event",
                    exception
            );
        }
    }
}
