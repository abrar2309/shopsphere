
package com.abrar.shopsphere.consumer;

import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.service.EventClaimRecoveryService;
import com.abrar.shopsphere.service.EventClaimService;
import com.abrar.shopsphere.service.EventIdempotencyService;
import com.abrar.shopsphere.service.EventProcessingCompletionService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentConsumerTest {

    @Mock
    private EventIdempotencyService eventIdempotencyService;

    @Mock
    private EventClaimService eventClaimService;

    @Mock
    private EventClaimRecoveryService eventClaimRecoveryService;

    @Mock
    private EventProcessingCompletionService
            eventProcessingCompletionService;

    @InjectMocks
    private PaymentConsumer paymentConsumer;

    private OrderCreatedEvent event;
    private ConsumerRecord<String, OrderCreatedEvent> record;
    private UUID eventId;
    private UUID claimToken;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        claimToken = UUID.randomUUID();

        event = new OrderCreatedEvent();
        event.setEventId(eventId);
        event.setCorrelationId("correlation-123");
        event.setOrderId(101L);
        event.setCustomerId(201L);
        event.setTotalAmount(499.99);
        event.setStatus("CREATED");

        record = new ConsumerRecord<>(
                "order-created",
                0,
                10L,
                "order-101",
                event
        );

        ReflectionTestUtils.setField(
                paymentConsumer, "failureMode", "NONE"
        );
    }

    @Test
    void shouldProcessNewEventAndCompleteClaim() {
        when(eventClaimService.tryClaimWithToken(eventId))
                .thenReturn(Optional.of(claimToken));

        when(eventIdempotencyService.alreadyProcessed(eventId))
                .thenReturn(false);

        paymentConsumer.consumeOrderCreated(event, record);

        verify(eventClaimService).tryClaimWithToken(eventId);

        verify(eventProcessingCompletionService)
                .completeProcessing(eventId, claimToken);

        verify(eventClaimService, never())
                .markAsFailed(any(), any());

        verifyNoMoreInteractions(eventProcessingCompletionService);
    }

    @Test
    void shouldMarkClaimAsFailedWhenOwnershipIsLostBeforeCompletion() {
        when(eventClaimService.tryClaimWithToken(eventId))
                .thenReturn(Optional.of(claimToken));

        when(eventIdempotencyService.alreadyProcessed(eventId))
                .thenReturn(false);

        doThrow(new IllegalStateException(
                "Claim ownership changed before completion. eventId="
                        + eventId
        )).when(eventProcessingCompletionService)
                .completeProcessing(eventId, claimToken);

        when(eventClaimService.markAsFailed(eventId, claimToken))
                .thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> paymentConsumer.consumeOrderCreated(event, record)
        );

        verify(eventProcessingCompletionService)
                .completeProcessing(eventId, claimToken);

        verify(eventClaimService)
                .markAsFailed(eventId, claimToken);
    }

    @Test
    void shouldCompleteClaimWhenEventWasAlreadyProcessed() {
        when(eventClaimService.tryClaimWithToken(eventId))
                .thenReturn(Optional.of(claimToken));

        when(eventIdempotencyService.alreadyProcessed(eventId))
                .thenReturn(true);

        when(eventClaimService.markAsCompleted(eventId, claimToken))
                .thenReturn(true);

        paymentConsumer.consumeOrderCreated(event, record);

        verify(eventClaimService)
                .markAsCompleted(eventId, claimToken);

        verify(eventIdempotencyService, never())
                .markAsProcessed(any());

        verify(eventProcessingCompletionService, never())
                .completeProcessing(any(), any());

        verify(eventClaimService, never())
                .markAsFailed(any(), any());
    }

    @Test
    void shouldMarkClaimAsFailedWhenDatabaseFailureIsSimulated() {
        ReflectionTestUtils.setField(
                paymentConsumer, "failureMode", "DATABASE"
        );

        when(eventClaimService.tryClaimWithToken(eventId))
                .thenReturn(Optional.of(claimToken));

        when(eventIdempotencyService.alreadyProcessed(eventId))
                .thenReturn(false);

        when(eventClaimService.markAsFailed(eventId, claimToken))
                .thenReturn(true);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> paymentConsumer.consumeOrderCreated(event, record)
        );

        assertEquals(
                "Simulated database failure",
                exception.getMessage()
        );

        verify(eventClaimService)
                .markAsFailed(eventId, claimToken);

        verify(eventProcessingCompletionService, never())
                .completeProcessing(any(), any());

        verify(eventIdempotencyService, never())
                .markAsProcessed(any());
    }

    @Test
    void shouldMarkClaimAsFailedWhenPoisonMessageIsSimulated() {
        ReflectionTestUtils.setField(
                paymentConsumer, "failureMode", "POISON"
        );

        when(eventClaimService.tryClaimWithToken(eventId))
                .thenReturn(Optional.of(claimToken));

        when(eventIdempotencyService.alreadyProcessed(eventId))
                .thenReturn(false);

        when(eventClaimService.markAsFailed(eventId, claimToken))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> paymentConsumer.consumeOrderCreated(event, record)
        );

        assertEquals(
                "Simulated poison message",
                exception.getMessage()
        );

        verify(eventClaimService)
                .markAsFailed(eventId, claimToken);

        verify(eventProcessingCompletionService, never())
                .completeProcessing(any(), any());

        verify(eventIdempotencyService, never())
                .markAsProcessed(any());
    }

    @Test
    void shouldIgnoreAlreadyProcessedDuplicate() {
        when(eventClaimService.tryClaimWithToken(eventId))
                .thenReturn(Optional.empty());

        when(eventClaimRecoveryService.tryRecoverWithToken(eventId))
                .thenReturn(Optional.empty());

        when(eventIdempotencyService.alreadyProcessed(eventId))
                .thenReturn(true);

        assertDoesNotThrow(
                () -> paymentConsumer.consumeOrderCreated(event, record)
        );

        verify(eventIdempotencyService)
                .alreadyProcessed(eventId);

        verify(eventIdempotencyService, never())
                .markAsProcessed(any());

        verify(eventClaimService, never())
                .markAsCompleted(any(), any());

        verify(eventProcessingCompletionService, never())
                .completeProcessing(any(), any());
    }

    @Test
    void shouldRecoverClaimAndProcessEvent() {
        when(eventClaimService.tryClaimWithToken(eventId))
                .thenReturn(Optional.empty());

        when(eventClaimRecoveryService.tryRecoverWithToken(eventId))
                .thenReturn(Optional.of(claimToken));

        when(eventIdempotencyService.alreadyProcessed(eventId))
                .thenReturn(false);

        paymentConsumer.consumeOrderCreated(event, record);

        verify(eventClaimRecoveryService)
                .tryRecoverWithToken(eventId);

        verify(eventProcessingCompletionService)
                .completeProcessing(eventId, claimToken);

        verify(eventClaimService, never())
                .markAsFailed(any(), any());
    }

    @Test
    void shouldThrowWhenClaimCannotBeAcquiredOrRecovered() {
        when(eventClaimService.tryClaimWithToken(eventId))
                .thenReturn(Optional.empty());

        when(eventClaimRecoveryService.tryRecoverWithToken(eventId))
                .thenReturn(Optional.empty());

        when(eventIdempotencyService.alreadyProcessed(eventId))
                .thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> paymentConsumer.consumeOrderCreated(event, record)
        );

        verify(eventIdempotencyService, never())
                .markAsProcessed(any());

        verify(eventProcessingCompletionService, never())
                .completeProcessing(any(), any());
    }

    @Test
    void shouldMarkClaimAsFailedWhenPaymentFails() {
        ReflectionTestUtils.setField(
                paymentConsumer, "failureMode", "PAYMENT"
        );

        when(eventClaimService.tryClaimWithToken(eventId))
                .thenReturn(Optional.of(claimToken));

        when(eventIdempotencyService.alreadyProcessed(eventId))
                .thenReturn(false);

        when(eventClaimService.markAsFailed(eventId, claimToken))
                .thenReturn(true);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> paymentConsumer.consumeOrderCreated(event, record)
        );

        assertEquals(
                "Simulated payment service failure",
                exception.getMessage()
        );

        verify(eventClaimService)
                .markAsFailed(eventId, claimToken);

        verify(eventProcessingCompletionService, never())
                .completeProcessing(any(), any());

        verify(eventIdempotencyService, never())
                .markAsProcessed(any());

        verify(eventClaimService, never())
                .markAsCompleted(any(), any());
    }
}
