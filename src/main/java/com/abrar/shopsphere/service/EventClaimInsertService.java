
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.EventClaim;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventClaimInsertService {

    private final JdbcTemplate jdbcTemplate;

    public EventClaimInsertService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertClaim(EventClaim eventClaim) {
        jdbcTemplate.update(
                """
                INSERT INTO event_claims
                    (event_id, claimed_at, lease_until, status, claim_token)
                VALUES (?, ?, ?, ?, ?)
                """,
                eventClaim.getEventId(),
                eventClaim.getClaimedAt(),
                eventClaim.getLeaseUntil(),
                eventClaim.getStatus().name(),
                eventClaim.getClaimToken()
        );
    }
}
