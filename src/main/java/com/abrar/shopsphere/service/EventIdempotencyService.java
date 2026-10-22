package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.ProcessedEvent;
import com.abrar.shopsphere.repository.ProcessedEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EventIdempotencyService {

    private final ProcessedEventRepository
            processedEventRepository;

    public EventIdempotencyService(
            ProcessedEventRepository processedEventRepository) {

        this.processedEventRepository =
                processedEventRepository;
    }

    public boolean alreadyProcessed(
            UUID eventId) {

        return processedEventRepository
                .existsById(eventId);
    }

    @Transactional
    public void markAsProcessed(
            UUID eventId) {

        ProcessedEvent processedEvent =
                new ProcessedEvent(
                        eventId,
                        LocalDateTime.now()
                );

        processedEventRepository.save(
                processedEvent
        );
    }
}