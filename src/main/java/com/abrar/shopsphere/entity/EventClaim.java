
package com.abrar.shopsphere.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_claims")
public class EventClaim {

    public enum ClaimStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    @Column(name = "lease_until", nullable = false)
    private LocalDateTime leaseUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClaimStatus status;

    @Column(name = "claim_token", nullable = false)
    private UUID claimToken;

    protected EventClaim() {
    }

    public EventClaim(
            UUID eventId,
            LocalDateTime claimedAt,
            LocalDateTime leaseUntil) {
        this.eventId = eventId;
        this.claimedAt = claimedAt;
        this.leaseUntil = leaseUntil;
        this.status = ClaimStatus.PROCESSING;
        this.claimToken = UUID.randomUUID();
    }

    public UUID getEventId() {
        return eventId;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public LocalDateTime getLeaseUntil() {
        return leaseUntil;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public void setClaimToken(UUID claimToken) {
        this.claimToken = claimToken;
    }
}
