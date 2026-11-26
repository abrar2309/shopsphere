
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.EventClaim;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class EventClaimRecoveryServiceTest {

    @Autowired
    private EventClaimService eventClaimService;

    @Autowired
    private EventClaimRecoveryService eventClaimRecoveryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID createExpiredClaim() {
        UUID eventId = UUID.randomUUID();

        assertTrue(eventClaimService.tryClaim(eventId));

        jdbcTemplate.update(
                """
                UPDATE event_claims
                   SET lease_until = ?
                 WHERE event_id = ?
                """,
                LocalDateTime.now().minusMinutes(1),
                eventId
        );

        return eventId;
    }

    @Test
    void expiredClaimShouldBeRecovered() {
        UUID eventId = createExpiredClaim();

        assertTrue(eventClaimRecoveryService.tryRecover(eventId));

        LocalDateTime leaseUntil = jdbcTemplate.queryForObject(
                """
                SELECT lease_until
                  FROM event_claims
                 WHERE event_id = ?
                """,
                LocalDateTime.class,
                eventId
        );

        assertTrue(leaseUntil.isAfter(LocalDateTime.now()));
    }

    @Test
    void failedClaimShouldBeRecoverable() {
        UUID eventId = UUID.randomUUID();

        UUID oldToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        assertTrue(
                eventClaimService.markAsFailed(eventId, oldToken)
        );

        UUID newToken = eventClaimRecoveryService
                .tryRecoverWithToken(eventId)
                .orElseThrow();

        assertFalse(oldToken.equals(newToken));

        assertEquals(
                EventClaim.ClaimStatus.PROCESSING,
                eventClaimService.findStatus(eventId)
        );
    }

    @Test
    void oldTokenShouldNotCompleteReclaimedFailedClaim() {
        UUID eventId = UUID.randomUUID();

        UUID oldToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        assertTrue(
                eventClaimService.markAsFailed(eventId, oldToken)
        );

        UUID newToken = eventClaimRecoveryService
                .tryRecoverWithToken(eventId)
                .orElseThrow();

        assertFalse(
                eventClaimService.markAsCompleted(eventId, oldToken)
        );

        assertTrue(
                eventClaimService.markAsCompleted(eventId, newToken)
        );

        assertEquals(
                EventClaim.ClaimStatus.COMPLETED,
                eventClaimService.findStatus(eventId)
        );
    }

    @Test
    void activeLeaseShouldNotBeRecovered() {
        UUID eventId = UUID.randomUUID();

        assertTrue(eventClaimService.tryClaim(eventId));

        assertFalse(eventClaimRecoveryService.tryRecover(eventId));
    }

    @Test
    void completedClaimShouldNotBeRecovered() {
        UUID eventId = UUID.randomUUID();

        UUID claimToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        assertTrue(
                eventClaimService.markAsCompleted(eventId, claimToken)
        );

        assertFalse(eventClaimRecoveryService.tryRecover(eventId));

        assertEquals(
                EventClaim.ClaimStatus.COMPLETED,
                eventClaimService.findStatus(eventId)
        );
    }

    @Test
    void recoveredClaimShouldNotBeRecoveredTwice() {
        UUID eventId = createExpiredClaim();

        assertTrue(eventClaimRecoveryService.tryRecover(eventId));

        assertFalse(eventClaimRecoveryService.tryRecover(eventId));
    }

    @Test
    void oldWorkerTokenShouldBeRejectedAfterRecovery() {
        UUID eventId = UUID.randomUUID();

        UUID oldToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        // Simulate the original worker's lease expiring.
        jdbcTemplate.update(
                """
                UPDATE event_claims
                   SET lease_until = ?
                 WHERE event_id = ?
                """,
                LocalDateTime.now().minusMinutes(1),
                eventId
        );

        UUID newToken = eventClaimRecoveryService
                .tryRecoverWithToken(eventId)
                .orElseThrow();

        assertFalse(oldToken.equals(newToken));

        // The original worker must no longer be able to complete the claim.
        assertFalse(
                eventClaimService.markAsCompleted(eventId, oldToken)
        );

        // The recovered worker can complete it with the replacement token.
        assertTrue(
                eventClaimService.markAsCompleted(eventId, newToken)
        );

        assertEquals(
                EventClaim.ClaimStatus.COMPLETED,
                eventClaimService.findStatus(eventId)
        );
    }

    @Test
    void concurrentRecoveryShouldHaveOnlyOneWinner() throws Exception {
        UUID eventId = createExpiredClaim();
        int workerCount = 5;

        ExecutorService executor =
                Executors.newFixedThreadPool(workerCount);

        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            @SuppressWarnings("unchecked")
            Future<Boolean>[] results = new Future[workerCount];

            for (int i = 0; i < workerCount; i++) {
                results[i] = executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    return eventClaimRecoveryService.tryRecover(eventId);
                });
            }

            ready.await();
            start.countDown();

            int successfulRecoveries = 0;

            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulRecoveries++;
                }
            }

            assertEquals(1, successfulRecoveries);
        } finally {
            executor.shutdownNow();
        }
    }
}
