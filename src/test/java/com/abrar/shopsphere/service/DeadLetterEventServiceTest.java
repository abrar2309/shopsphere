
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.DeadLetterEvent;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.repository.DeadLetterEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadLetterEventServiceTest {

    @Mock
    private DeadLetterEventRepository repository;

    private DeadLetterEventService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterEventService(repository);
    }

    @Test
    void shouldPersistFailureDetails() {
        UUID eventId = UUID.randomUUID();

        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setEventId(eventId);
        event.setCorrelationId("correlation-123");
        event.setOrderId(42L);

        when(repository.findByEventId(eventId))
                .thenReturn(Optional.empty());

        when(repository.saveAndFlush(any(DeadLetterEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.recordFailure(
                event,
                "order-created.DLT",
                1,
                25L,
                "order-key-42",
                "java.lang.RuntimeException",
                "Payment processing failed",
                "{\"eventId\":\"" + eventId + "\"}"
        );

        ArgumentCaptor<DeadLetterEvent> captor =
                ArgumentCaptor.forClass(DeadLetterEvent.class);

        verify(repository).saveAndFlush(captor.capture());

        DeadLetterEvent saved = captor.getValue();

        assertEquals(eventId, saved.getEventId());
        assertEquals("correlation-123", saved.getCorrelationId());
        assertEquals(42L, saved.getOrderId());
        assertEquals("order-created.DLT", saved.getTopic());
        assertEquals(1, saved.getPartitionNumber());
        assertEquals(25L, saved.getRecordOffset());
        assertEquals("order-key-42", saved.getMessageKey());
        assertEquals(
                "java.lang.RuntimeException",
                saved.getExceptionClass()
        );
        assertEquals(
                "Payment processing failed",
                saved.getExceptionMessage()
        );
        assertEquals(
                "{\"eventId\":\"" + eventId + "\"}",
                saved.getPayload()
        );
        assertEquals(DeadLetterEvent.FailureStatus.FAILED,
                saved.getStatus());
        assertNotNull(saved.getFailedAt());
    }

    @Test
    void shouldNotSaveDuplicateEvent() {
        UUID eventId = UUID.randomUUID();

        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setEventId(eventId);

        when(repository.findByEventId(eventId))
                .thenReturn(Optional.of(new DeadLetterEvent()));

        service.recordFailure(
                event,
                "order-created.DLT",
                0,
                10L,
                "order-key",
                "RuntimeException",
                "Failure",
                "{}"
        );

        verify(repository, never())
                .saveAndFlush(any(DeadLetterEvent.class));
    }

    @Test
    void shouldRejectEventWithoutEventId() {
        OrderCreatedEvent event = new OrderCreatedEvent();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordFailure(
                        event,
                        "order-created.DLT",
                        0,
                        10L,
                        "order-key",
                        "RuntimeException",
                        "Failure",
                        "{}"
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldPropagateUnexpectedDatabaseFailure() {
        UUID eventId = UUID.randomUUID();

        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setEventId(eventId);

        when(repository.findByEventId(eventId))
                .thenReturn(Optional.empty());

        when(repository.saveAndFlush(any(DeadLetterEvent.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Simulated database failure"
                ));

        when(repository.findByEventId(eventId))
                .thenReturn(Optional.empty());

        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.recordFailure(
                        event,
                        "order-created.DLT",
                        0,
                        10L,
                        "order-key",
                        "RuntimeException",
                        "Failure",
                        "{}"
                )
        );
    }

    @Test
    void shouldNotFailWhenConcurrentDeliveryAlreadySavedEvent() {
        UUID eventId = UUID.randomUUID();

        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setEventId(eventId);

        when(repository.findByEventId(eventId))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(new DeadLetterEvent())
                );

        when(repository.saveAndFlush(any(DeadLetterEvent.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate event ID"
                ));

        assertDoesNotThrow(
                () -> service.recordFailure(
                        event,
                        "order-created.DLT",
                        0,
                        10L,
                        "order-key",
                        "RuntimeException",
                        "Failure",
                        "{}"
                )
        );
    }
}
