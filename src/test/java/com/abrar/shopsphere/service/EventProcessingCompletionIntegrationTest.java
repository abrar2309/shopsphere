
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.EventClaim;
import com.abrar.shopsphere.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class EventProcessingCompletionIntegrationTest {

    @Autowired
    private EventProcessingCompletionService completionService;

    @Autowired
    private EventClaimService eventClaimService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistCompletedClaimAndProcessedEventTogether() {
        // Arrange: create a real claim in the H2 test database.
        UUID eventId = UUID.randomUUID();

        UUID claimToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        assertFalse(processedEventRepository.existsById(eventId));

        // Act: complete processing using the real service.
        completionService.completeProcessing(eventId, claimToken);

        // Assert: the claim and processed-event record both exist.
        assertEquals(
                EventClaim.ClaimStatus.COMPLETED,
                eventClaimService.findStatus(eventId)
        );

        assertTrue(
                processedEventRepository.existsById(eventId),
                "Successfully completed events must have a processed-event record"
        );
    }

    @Test
    void shouldNotRecordEventWhenClaimTokenIsInvalid() {
        UUID eventId = UUID.randomUUID();

        UUID actualToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        UUID invalidToken = UUID.randomUUID();

        // An invalid token must not complete the claim.
        assertThrows(
                IllegalStateException.class,
                () -> completionService.completeProcessing(
                        eventId,
                        invalidToken
                )
        );

        // Neither the claim nor the processed-event record should be changed.
        assertEquals(
                EventClaim.ClaimStatus.PROCESSING,
                eventClaimService.findStatus(eventId)
        );

        assertFalse(processedEventRepository.existsById(eventId));

        // Clean up the claim created by this test.
        assertTrue(eventClaimService.markAsFailed(eventId, actualToken));
    }

    @Test
    void shouldNotRecordSameEventTwiceWhenCompletionIsRepeated() {
        UUID eventId = UUID.randomUUID();

        UUID claimToken = eventClaimService
                .tryClaimWithToken(eventId)
                .orElseThrow();

        // First completion must succeed.
        completionService.completeProcessing(eventId, claimToken);

        assertEquals(
                EventClaim.ClaimStatus.COMPLETED,
                eventClaimService.findStatus(eventId)
        );

        // The same token must not complete the already-completed claim again.
        assertThrows(
                IllegalStateException.class,
                () -> completionService.completeProcessing(eventId, claimToken)
        );

        // Verify that exactly one processed-event record exists.
        Long recordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Long.class,
                eventId
        );

        assertEquals(
                1L,
                recordCount,
                "Repeated completion must not create another processed-event record"
        );
    }
}
