
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.DeadLetterEvent;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.repository.DeadLetterEventRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DeadLetterEventService {

    private final DeadLetterEventRepository repository;

    public DeadLetterEventService(
            DeadLetterEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordFailure(
            OrderCreatedEvent event,
            String topic,
            int partition,
            long offset,
            String messageKey,
            String exceptionClass,
            String exceptionMessage,
            String payload) {

        if (event == null || event.getEventId() == null) {
            throw new IllegalArgumentException(
                    "Cannot persist a dead-letter event without an event ID"
            );
        }

        if (repository.findByEventId(event.getEventId()).isPresent()) {
            return;
        }

        DeadLetterEvent failure = new DeadLetterEvent();

        failure.setEventId(event.getEventId());
        failure.setCorrelationId(event.getCorrelationId());
        failure.setOrderId(event.getOrderId());
        failure.setTopic(topic);
        failure.setPartitionNumber(partition);
        failure.setRecordOffset(offset);
        failure.setMessageKey(messageKey);
        failure.setExceptionClass(exceptionClass);
        failure.setExceptionMessage(exceptionMessage);
        failure.setPayload(payload);
        failure.setFailedAt(LocalDateTime.now());
        failure.setStatus(
                DeadLetterEvent.FailureStatus.FAILED
        );

        try {
            repository.saveAndFlush(failure);
        } catch (DataIntegrityViolationException exception) {
            // If another delivery saved this event concurrently,
            // don't create a duplicate record.
            if (repository.findByEventId(event.getEventId()).isEmpty()) {
                throw exception;
            }
        }
    }
}
