
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.EventClaim;
import com.abrar.shopsphere.repository.EventClaimRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventClaimService {

    private static final long CLAIM_LEASE_MINUTES = 5;

    private final EventClaimRepository eventClaimRepository;
    private final EventClaimInsertService eventClaimInsertService;
    private final JdbcTemplate jdbcTemplate;

    public EventClaimService(
            EventClaimRepository eventClaimRepository,
            EventClaimInsertService eventClaimInsertService,
            JdbcTemplate jdbcTemplate
    ) {
        this.eventClaimRepository = eventClaimRepository;
        this.eventClaimInsertService = eventClaimInsertService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Backward-compatible method for existing callers and tests.
    public boolean tryClaim(UUID eventId) {
        return tryClaimWithToken(eventId).isPresent();
    }

    // Claims the event and returns the token belonging to this claim.
    public Optional<UUID> tryClaimWithToken(UUID eventId) {
        LocalDateTime now = LocalDateTime.now();

        EventClaim claim = new EventClaim(
                eventId,
                now,
                now.plusMinutes(CLAIM_LEASE_MINUTES)
        );

        try {
            eventClaimInsertService.insertClaim(claim);
            return Optional.of(claim.getClaimToken());
        } catch (DataIntegrityViolationException exception) {
            // Another worker already inserted this event's claim.
            return Optional.empty();
        }
    }

    public boolean markAsCompleted(UUID eventId, UUID claimToken) {
        return updateStatus(
                eventId,
                claimToken,
                EventClaim.ClaimStatus.COMPLETED
        );
    }

    public boolean markAsFailed(UUID eventId, UUID claimToken) {
        return updateStatus(
                eventId,
                claimToken,
                EventClaim.ClaimStatus.FAILED
        );
    }

    private boolean updateStatus(
            UUID eventId,
            UUID claimToken,
            EventClaim.ClaimStatus newStatus
    ) {
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE event_claims
                   SET status = ?
                 WHERE event_id = ?
                   AND claim_token = ?
                   AND status = ?
                """,
                newStatus.name(),
                eventId,
                claimToken,
                EventClaim.ClaimStatus.PROCESSING.name()
        );

        return updatedRows == 1;
    }

    @Transactional(readOnly = true)
    public EventClaim.ClaimStatus findStatus(UUID eventId) {
        return eventClaimRepository.findById(eventId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No claim found for event: " + eventId
                        )
                )
                .getStatus();
    }
}
