
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.OutboxEvent;
import com.abrar.shopsphere.event.OrderCreatedEvent;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxClaimService outboxClaimService;

    @Mock
    private OutboxStatusService outboxStatusService;

    @Mock
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private OutboxEvent outboxEvent;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private OrderCreatedEvent createOrderEvent() {

        return new OrderCreatedEvent(
                UUID.randomUUID(),
                "test-correlation-id",
                101L,
                202L,
                99.0,
                "CREATED",
                LocalDateTime.now(),
                List.of()
        );
    }

    private void arrangeOutboxEvent(OrderCreatedEvent event)
            throws Exception {

        when(outboxEvent.getId()).thenReturn(1L);

        when(outboxEvent.getEventId())
                .thenReturn(event.getEventId());

        when(outboxEvent.getEventType())
                .thenReturn("OrderCreated");

        when(outboxEvent.getPayload())
                .thenReturn("{\"test\":true}");

        when(outboxClaimService.claimEvents(
                eq(20),
                any(UUID.class)
        )).thenReturn(List.of(outboxEvent));

        when(objectMapper.readValue(
                anyString(),
                eq(OrderCreatedEvent.class)
        )).thenReturn(event);
    }

    @Test
    void shouldMarkEventAsPublishedWhenKafkaSendSucceeds()
            throws Exception {

        OrderCreatedEvent event = createOrderEvent();

        arrangeOutboxEvent(event);

        CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
                CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(
                eq("order-created"),
                eq("101"),
                same(event)
        )).thenReturn(future);

        outboxPublisher.publishUnpublishedEvents();

        verify(kafkaTemplate).send(
                "order-created",
                "101",
                event
        );

        verify(outboxStatusService).markAsPublished(
                eq(1L),
                any(UUID.class)
        );
    }

    @Test
    void shouldNotMarkEventAsPublishedWhenKafkaSendFails()
            throws Exception {

        OrderCreatedEvent event = createOrderEvent();

        arrangeOutboxEvent(event);

        CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
                CompletableFuture.failedFuture(
                        new RuntimeException("Simulated Kafka failure")
                );

        when(kafkaTemplate.send(
                eq("order-created"),
                eq("101"),
                same(event)
        )).thenReturn(future);

        outboxPublisher.publishUnpublishedEvents();

        verify(kafkaTemplate).send(
                "order-created",
                "101",
                event
        );

        verify(outboxStatusService, never()).markAsPublished(
                any(Long.class),
                any(UUID.class)
        );
    }

    @Test
    void shouldNotSendEventWhenPayloadDeserializationFails()
            throws Exception {

        OrderCreatedEvent event = createOrderEvent();

        when(outboxEvent.getId()).thenReturn(1L);

        when(outboxEvent.getEventId())
                .thenReturn(event.getEventId());

        when(outboxEvent.getEventType())
                .thenReturn("OrderCreated");

        when(outboxEvent.getPayload())
                .thenReturn("invalid-json");

        when(outboxClaimService.claimEvents(
                eq(20),
                any(UUID.class)
        )).thenReturn(List.of(outboxEvent));

        when(objectMapper.readValue(
                anyString(),
                eq(OrderCreatedEvent.class)
        )).thenThrow(
                new com.fasterxml.jackson.core.JsonProcessingException(
                        "Invalid test payload"
                ) {}
        );

        outboxPublisher.publishUnpublishedEvents();

        verify(kafkaTemplate, never()).send(
                anyString(),
                anyString(),
                any(OrderCreatedEvent.class)
        );

        verify(outboxStatusService, never()).markAsPublished(
                any(Long.class),
                any(UUID.class)
        );
    }
}
