
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.ProcessedEvent;
import com.abrar.shopsphere.repository.ProcessedEventRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class EventIdempotencyServiceTest {

    @Autowired
    private EventIdempotencyService eventIdempotencyService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void newEventShouldNotBeProcessed() {
        UUID eventId = UUID.randomUUID();

        assertFalse(
                eventIdempotencyService.alreadyProcessed(eventId)
        );
    }

    @Test
    void recordedEventShouldBeRecognizedAsProcessed() {
        UUID eventId = UUID.randomUUID();

        eventIdempotencyService.markAsProcessed(eventId);

        assertTrue(
                eventIdempotencyService.alreadyProcessed(eventId)
        );
    }

    @Test
    void processedEventShouldRemainRecognizedOnRepeatedDelivery() {
        UUID eventId = UUID.randomUUID();

        eventIdempotencyService.markAsProcessed(eventId);

        // First delivery has completed.
        assertTrue(
                eventIdempotencyService.alreadyProcessed(eventId)
        );

        // Repeated delivery with the same event ID is recognized.
        assertTrue(
                eventIdempotencyService.alreadyProcessed(eventId)
        );
    }

    @Test
    void databaseShouldRejectDuplicateEventIds() {
        UUID eventId = UUID.randomUUID();

        eventIdempotencyService.markAsProcessed(eventId);

        assertTrue(
                eventIdempotencyService.alreadyProcessed(eventId)
        );

        // Execute a genuine second SQL INSERT instead of JPA save().
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO processed_events (event_id, processed_at) VALUES (?, ?)",
                        eventId,
                        java.sql.Timestamp.valueOf(
                                java.time.LocalDateTime.now()
                        )
                )
        );
    }
}
