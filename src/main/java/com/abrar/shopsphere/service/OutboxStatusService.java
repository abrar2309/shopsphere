package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.OutboxEvent;
import com.abrar.shopsphere.repository.OutboxEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OutboxStatusService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxStatusService(
            OutboxEventRepository outboxEventRepository) {

        this.outboxEventRepository =
                outboxEventRepository;
    }

    @Transactional
    public void markAsPublished(
            Long outboxId,
            UUID claimToken) {

        OutboxEvent event =
                outboxEventRepository
                        .findById(outboxId)
                        .orElse(null);

        if (event == null) {
            return;
        }

        if (event.isPublished()) {
            return;
        }

        if (!claimToken.equals(
                event.getClaimToken())) {

            return;
        }

        event.setPublished(true);
        event.setPublishedAt(
                LocalDateTime.now()
        );

        event.setClaimedAt(null);
        event.setClaimToken(null);

        outboxEventRepository.save(event);
    }
}