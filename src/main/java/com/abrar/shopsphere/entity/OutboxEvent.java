package com.abrar.shopsphere.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(
                        name = "idx_outbox_unpublished",
                        columnList = "published, created_at"
                )
        }
)
public class OutboxEvent {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @Column(
            name = "event_id",
            nullable = false,
            unique = true
    )
    private UUID eventId;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "claim_token")
    private UUID claimToken;
    @Column(
            name = "event_type",
            nullable = false
    )
    private String eventType;


    @Column(
            name = "aggregate_id",
            nullable = false
    )
    private String aggregateId;


    @Column(
            name = "payload",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String payload;


    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;


    @Column(
            name = "published",
            nullable = false
    )
    private boolean published;


    @Column(
            name = "published_at"
    )
    private LocalDateTime publishedAt;


    public OutboxEvent() {
    }


    public Long getId() {
        return id;
    }


    public UUID getEventId() {
        return eventId;
    }


    public void setEventId(
            UUID eventId) {

        this.eventId =
                eventId;
    }


    public String getEventType() {
        return eventType;
    }


    public void setEventType(
            String eventType) {

        this.eventType =
                eventType;
    }


    public String getAggregateId() {
        return aggregateId;
    }


    public void setAggregateId(
            String aggregateId) {

        this.aggregateId =
                aggregateId;
    }


    public String getPayload() {
        return payload;
    }


    public void setPayload(
            String payload) {

        this.payload =
                payload;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(
            LocalDateTime createdAt) {

        this.createdAt =
                createdAt;
    }


    public boolean isPublished() {
        return published;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public void setClaimToken(UUID claimToken) {
        this.claimToken = claimToken;
    }
    public void setPublished(
            boolean published) {

        this.published =
                published;
    }


    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }


    public void setPublishedAt(
            LocalDateTime publishedAt) {

        this.publishedAt =
                publishedAt;
    }
}