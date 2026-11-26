
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.EventClaim;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class EventProcessingCompletionRollbackTest {

    @Autowired
    private EventProcessingCompletionService completionService;

    @Autowired
    private EventClaimService eventClaimService;

    @MockitoBean
    private EventIdempotencyService eventIdempotencyService;

    @Test
    void shouldRollbackClaimCompletionWhenRecordingProcessedEventFails() {
        // Arrange: create a real claim in the H2 test database.
        UUID eventId = UUID.randomUUID();

        UUID claimToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        assertEquals(
                EventClaim.ClaimStatus.PROCESSING,
                eventClaimService.findStatus(eventId)
        );

        // Simulate a failure while recording the processed event.
        doThrow(new IllegalStateException(
                "Simulated processed-event database failure"
        )).when(eventIdempotencyService).markAsProcessed(eventId);

        // Act: the claim update should be rolled back when the
        // subsequent operation throws an exception.
        assertThrows(
                IllegalStateException.class,
                () -> completionService.completeProcessing(
                        eventId,
                        claimToken
                )
        );

        // Assert: the real database claim must still be PROCESSING.
        assertEquals(
                EventClaim.ClaimStatus.PROCESSING,
                eventClaimService.findStatus(eventId),
                "Claim completion should roll back when event recording fails"
        );

        verify(eventIdempotencyService).markAsProcessed(eventId);
    }

    @Test
    void shouldNotCompleteClaimWhenTokenIsIncorrect() {
        UUID eventId = UUID.randomUUID();

        UUID actualToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        UUID incorrectToken = UUID.randomUUID();

        assertThrows(
                IllegalStateException.class,
                () -> completionService.completeProcessing(
                        eventId,
                        incorrectToken
                )
        );

        assertEquals(
                EventClaim.ClaimStatus.PROCESSING,
                eventClaimService.findStatus(eventId)
        );

        // The idempotency operation must not run without valid ownership.
        org.mockito.Mockito.verifyNoInteractions(
                eventIdempotencyService
        );

        // Clean up this test's claim using its valid token.
        assertTrue(
                eventClaimService.markAsFailed(eventId, actualToken)
        );
    }
}
