package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.OutboxEvent;
import com.abrar.shopsphere.repository.OutboxEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxClaimService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxClaimService(
            OutboxEventRepository outboxEventRepository) {

        this.outboxEventRepository =
                outboxEventRepository;
    }

    @Transactional
    public List<OutboxEvent> claimEvents(
            int batchSize,
            UUID claimToken) {

        LocalDateTime claimExpiry =
                LocalDateTime.now().minusMinutes(5);

        List<OutboxEvent> events =
                outboxEventRepository.claimableEvents(
                        claimExpiry,
                        batchSize
                );

        LocalDateTime now =
                LocalDateTime.now();

        for (OutboxEvent event : events) {

            event.setClaimToken(claimToken);
            event.setClaimedAt(now);
        }

        outboxEventRepository.saveAll(events);

        return events;
    }
}