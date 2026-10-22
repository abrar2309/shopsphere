package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.entity.OutboxEvent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {

    @Query(
            value = """
            SELECT *
            FROM outbox_events
            WHERE published = false
              AND (
                    claimed_at IS NULL
                    OR claimed_at < :claimExpiry
                  )
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """,
            nativeQuery = true
    )
    List<OutboxEvent> claimableEvents(
            @Param("claimExpiry")
            LocalDateTime claimExpiry,

            @Param("batchSize")
            int batchSize
    );
}