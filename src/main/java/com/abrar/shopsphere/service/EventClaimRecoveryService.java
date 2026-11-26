
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.EventClaim;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventClaimRecoveryService {

    private static final long CLAIM_LEASE_MINUTES = 5;

    private final JdbcTemplate jdbcTemplate;

    public EventClaimRecoveryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Backward-compatible method for existing callers and tests.
    public boolean tryRecover(UUID eventId) {
        return tryRecoverWithToken(eventId).isPresent();
    }

    // Recover an expired PROCESSING claim or reclaim a FAILED claim.
    public Optional<UUID> tryRecoverWithToken(UUID eventId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newLeaseUntil =
                now.plusMinutes(CLAIM_LEASE_MINUTES);

        UUID newClaimToken = UUID.randomUUID();

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE event_claims
                   SET claimed_at = ?,
                       lease_until = ?,
                       claim_token = ?,
                       status = ?
                 WHERE event_id = ?
                   AND (
                       (status = ? AND lease_until <= ?)
                       OR status = ?
                   )
                """,
                now,
                newLeaseUntil,
                newClaimToken,
                EventClaim.ClaimStatus.PROCESSING.name(),
                eventId,
                EventClaim.ClaimStatus.PROCESSING.name(),
                now,
                EventClaim.ClaimStatus.FAILED.name()
        );

        return updatedRows == 1
                ? Optional.of(newClaimToken)
                : Optional.empty();
    }
}
