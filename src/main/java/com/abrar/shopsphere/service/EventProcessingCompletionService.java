
package com.abrar.shopsphere.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventProcessingCompletionService {

    private final EventClaimService eventClaimService;
    private final EventIdempotencyService eventIdempotencyService;

    public EventProcessingCompletionService(
            EventClaimService eventClaimService,
            EventIdempotencyService eventIdempotencyService
    ) {
        this.eventClaimService = eventClaimService;
        this.eventIdempotencyService = eventIdempotencyService;
    }

    @Transactional
    public void completeProcessing(
            UUID eventId,
            UUID claimToken
    ) {
        boolean completed = eventClaimService.markAsCompleted(
                eventId,
                claimToken
        );

        if (!completed) {
            throw new IllegalStateException(
                    "Claim ownership changed before completion. eventId="
                            + eventId
            );
        }

        eventIdempotencyService.markAsProcessed(eventId);
    }
}
