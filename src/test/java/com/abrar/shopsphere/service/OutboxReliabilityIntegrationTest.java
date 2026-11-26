
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.OutboxEvent;
import com.abrar.shopsphere.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class OutboxReliabilityIntegrationTest {

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxStatusService outboxStatusService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OutboxEvent createUnpublishedEvent() {
        OutboxEvent event = new OutboxEvent();

        event.setEventId(UUID.randomUUID());
        event.setEventType("OrderCreated");
        event.setAggregateId("test-order-" + UUID.randomUUID());
        event.setPayload("{\"test\":true}");
        event.setCreatedAt(LocalDateTime.now());
        event.setPublished(false);

        return outboxEventRepository.saveAndFlush(event);
    }

    @Test
    void shouldClaimAnUnpublishedEvent() {
        OutboxEvent created = createUnpublishedEvent();
        UUID claimToken = UUID.randomUUID();

        List<OutboxEvent> claimed =
                outboxClaimService.claimEvents(10, claimToken);

        OutboxEvent result = claimed.stream()
                .filter(event -> created.getId().equals(event.getId()))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Expected event to be claimed"));

        assertEquals(claimToken, result.getClaimToken());
        assertNotNull(result.getClaimedAt());
        assertFalse(result.isPublished());
    }

    @Test
    void shouldNotClaimTheSameEventAgainWhileItsLeaseIsActive() {
        OutboxEvent created = createUnpublishedEvent();

        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();

        List<OutboxEvent> firstBatch =
                outboxClaimService.claimEvents(10, firstToken);

        assertTrue(firstBatch.stream()
                .anyMatch(event -> created.getId().equals(event.getId())));

        List<OutboxEvent> secondBatch =
                outboxClaimService.claimEvents(10, secondToken);

        assertFalse(secondBatch.stream()
                .anyMatch(event -> created.getId().equals(event.getId())));

        OutboxEvent stored = outboxEventRepository
                .findById(created.getId())
                .orElseThrow();

        assertEquals(firstToken, stored.getClaimToken());
    }

    @Test
    void shouldRecoverAnExpiredClaimWithANewToken() {
        OutboxEvent created = createUnpublishedEvent();

        UUID oldToken = UUID.randomUUID();
        UUID newToken = UUID.randomUUID();

        created.setClaimToken(oldToken);
        created.setClaimedAt(LocalDateTime.now().minusMinutes(10));
        outboxEventRepository.saveAndFlush(created);

        List<OutboxEvent> recovered =
                outboxClaimService.claimEvents(10, newToken);

        assertTrue(recovered.stream()
                .anyMatch(event -> created.getId().equals(event.getId())));

        OutboxEvent stored = outboxEventRepository
                .findById(created.getId())
                .orElseThrow();

        assertEquals(newToken, stored.getClaimToken());
        assertNotNull(stored.getClaimedAt());
    }

    @Test
    void staleTokenMustNotMarkEventAsPublished() {
        OutboxEvent created = createUnpublishedEvent();

        UUID currentToken = UUID.randomUUID();
        UUID staleToken = UUID.randomUUID();

        created.setClaimToken(currentToken);
        created.setClaimedAt(LocalDateTime.now());
        outboxEventRepository.saveAndFlush(created);

        outboxStatusService.markAsPublished(
                created.getId(),
                staleToken
        );

        OutboxEvent stored = outboxEventRepository
                .findById(created.getId())
                .orElseThrow();

        assertFalse(stored.isPublished());
        assertEquals(currentToken, stored.getClaimToken());
    }

    @Test
    void validTokenShouldMarkEventAsPublished() {
        OutboxEvent created = createUnpublishedEvent();
        UUID claimToken = UUID.randomUUID();

        created.setClaimToken(claimToken);
        created.setClaimedAt(LocalDateTime.now());
        outboxEventRepository.saveAndFlush(created);

        outboxStatusService.markAsPublished(
                created.getId(),
                claimToken
        );

        OutboxEvent stored = outboxEventRepository
                .findById(created.getId())
                .orElseThrow();

        assertTrue(stored.isPublished());
        assertNotNull(stored.getPublishedAt());
        assertEquals(null, stored.getClaimToken());
        assertEquals(null, stored.getClaimedAt());
    }

    @Test
    void concurrentWorkersShouldNotClaimTheSameEventTwice()
            throws Exception {

        OutboxEvent created = createUnpublishedEvent();

        int workerCount = 5;

        ExecutorService executor =
                Executors.newFixedThreadPool(workerCount);

        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            @SuppressWarnings("unchecked")
            Future<Boolean>[] results = new Future[workerCount];

            for (int i = 0; i < workerCount; i++) {
                UUID token = UUID.randomUUID();

                results[i] = executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    List<OutboxEvent> claimed =
                            outboxClaimService.claimEvents(10, token);

                    return claimed.stream()
                            .anyMatch(event ->
                                    created.getId().equals(event.getId()));
                });
            }

            ready.await();
            start.countDown();

            int winners = 0;

            for (Future<Boolean> result : results) {
                if (result.get()) {
                    winners++;
                }
            }

            assertEquals(
                    1,
                    winners,
                    "Exactly one worker should claim the event"
            );
        } finally {
            executor.shutdownNow();
        }
    }
}
