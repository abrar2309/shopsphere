
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.OutboxEvent;
import com.abrar.shopsphere.repository.OutboxEventRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OutboxRetryLifecycleIntegrationTest {

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxStatusService outboxStatusService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void failedPublishCanBeRecoveredAndCompletedByNewWorker() {
        // Arrange: create a new unpublished event.
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("OrderCreated");
        event.setAggregateId("retry-test-" + UUID.randomUUID());
        event.setPayload("{\"test\":true}");
        event.setCreatedAt(LocalDateTime.now());
        event.setPublished(false);

        event = outboxEventRepository.saveAndFlush(event);

        Long eventId = event.getId();

        // Worker 1 claims the event.
        UUID oldToken = UUID.randomUUID();

        List<OutboxEvent> firstBatch =
                outboxClaimService.claimEvents(100, oldToken);

        assertTrue(
                firstBatch.stream()
                        .anyMatch(item -> eventId.equals(item.getId())),
                "First worker should claim the event"
        );

        // Simulate Kafka failure: do not mark the event published.
        OutboxEvent afterFailure = outboxEventRepository
                .findById(eventId)
                .orElseThrow();

        assertFalse(afterFailure.isPublished());

        // Simulate the lease expiring.
        afterFailure.setClaimedAt(
                LocalDateTime.now().minusMinutes(10)
        );
        outboxEventRepository.saveAndFlush(afterFailure);

        // Worker 2 recovers the event using a new token.
        UUID newToken = UUID.randomUUID();

        List<OutboxEvent> recoveredBatch =
                outboxClaimService.claimEvents(100, newToken);

        assertTrue(
                recoveredBatch.stream()
                        .anyMatch(item -> eventId.equals(item.getId())),
                "Second worker should reclaim the expired event"
        );

        OutboxEvent afterRecovery = outboxEventRepository
                .findById(eventId)
                .orElseThrow();

        assertEquals(newToken, afterRecovery.getClaimToken());
        assertFalse(afterRecovery.isPublished());

        // Worker 1 must not publish using its stale token.
        outboxStatusService.markAsPublished(eventId, oldToken);

        OutboxEvent afterStaleAttempt = outboxEventRepository
                .findById(eventId)
                .orElseThrow();

        assertFalse(
                afterStaleAttempt.isPublished(),
                "Stale worker must not mark the event as published"
        );
        assertEquals(newToken, afterStaleAttempt.getClaimToken());

        // Worker 2 successfully completes the publishing lifecycle.
        outboxStatusService.markAsPublished(eventId, newToken);

        OutboxEvent completed = outboxEventRepository
                .findById(eventId)
                .orElseThrow();

        assertTrue(completed.isPublished());
        assertNotNull(completed.getPublishedAt());
        assertNull(completed.getClaimToken());
        assertNull(completed.getClaimedAt());
    }
}
