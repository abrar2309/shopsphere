
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.EventClaim;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
class EventClaimServiceTest {

    @Autowired
    private EventClaimService eventClaimService;

    @Test
    void firstClaimShouldSucceed() {
        UUID eventId = UUID.randomUUID();

        assertTrue(eventClaimService.tryClaim(eventId));
    }

    @Test
    void duplicateClaimShouldFail() {
        UUID eventId = UUID.randomUUID();

        assertTrue(eventClaimService.tryClaim(eventId));
        assertFalse(eventClaimService.tryClaim(eventId));
    }

    @Test
    void concurrentClaimsShouldHaveOnlyOneWinner() throws Exception {
        UUID eventId = UUID.randomUUID();
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

                    return eventClaimService.tryClaim(eventId);
                });
            }

            ready.await();
            start.countDown();

            int successfulClaims = 0;

            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulClaims++;
                }
            }

            assertEquals(1, successfulClaims);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void completedClaimShouldBeMarkedAsCompleted() {
        UUID eventId = UUID.randomUUID();

        UUID claimToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        assertTrue(
                eventClaimService.markAsCompleted(eventId, claimToken)
        );

        assertEquals(
                EventClaim.ClaimStatus.COMPLETED,
                eventClaimService.findStatus(eventId)
        );
    }

    @Test
    void failedClaimShouldBeMarkedAsFailed() {
        UUID eventId = UUID.randomUUID();

        UUID claimToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        assertTrue(
                eventClaimService.markAsFailed(eventId, claimToken)
        );

        assertEquals(
                EventClaim.ClaimStatus.FAILED,
                eventClaimService.findStatus(eventId)
        );
    }

    @Test
    void wrongClaimTokenShouldNotCompleteClaim() {
        UUID eventId = UUID.randomUUID();

        UUID actualToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        UUID wrongToken = UUID.randomUUID();

        assertFalse(
                eventClaimService.markAsCompleted(eventId, wrongToken)
        );

        assertEquals(
                EventClaim.ClaimStatus.PROCESSING,
                eventClaimService.findStatus(eventId)
        );

        assertTrue(
                eventClaimService.markAsCompleted(eventId, actualToken)
        );
    }
}
