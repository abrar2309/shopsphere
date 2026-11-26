
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.OutboxEvent;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.repository.OutboxEventRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class OutboxServiceIntegrationTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private OrderCreatedEvent createEvent(UUID eventId) {
        return new OrderCreatedEvent(
                eventId,
                "test-correlation-id",
                101L,
                202L,
                99.0,
                "CREATED",
                LocalDateTime.now(),
                List.of()
        );
    }

    @Test
    void shouldPersistOrderCreatedEventAsUnpublishedOutboxRecord() {

        UUID eventId = UUID.randomUUID();

        OrderCreatedEvent event = createEvent(eventId);

        outboxService.saveOrderCreatedEvent(event);

        OutboxEvent stored = outboxEventRepository.findAll()
                .stream()
                .filter(item -> eventId.equals(item.getEventId()))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "Expected outbox event to be persisted"
                        )
                );

        assertEquals(eventId, stored.getEventId());
        assertEquals("OrderCreated", stored.getEventType());
        assertEquals("101", stored.getAggregateId());
        assertNotNull(stored.getPayload());
        assertFalse(stored.isPublished());
        assertNotNull(stored.getCreatedAt());
    }

    @Test
    void shouldRejectDuplicateOutboxEventId() {

        UUID eventId = UUID.randomUUID();

        // First event must be persisted successfully.
        outboxService.saveOrderCreatedEvent(createEvent(eventId));

        // Second event with the same ID must be rejected.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> outboxService.saveOrderCreatedEvent(
                        createEvent(eventId)
                )
        );

        // Verify that exactly one event with this ID remains.
        long matchingEvents = outboxEventRepository.findAll()
                .stream()
                .filter(item -> eventId.equals(item.getEventId()))
                .count();

        assertEquals(
                1,
                matchingEvents,
                "Duplicate event must not create another outbox record"
        );
    }
}
