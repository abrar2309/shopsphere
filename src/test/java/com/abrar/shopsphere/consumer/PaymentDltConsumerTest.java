
package com.abrar.shopsphere.consumer;

import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.service.DeadLetterEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentDltConsumerTest {

    @Mock
    private DeadLetterEventService deadLetterEventService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentDltConsumer paymentDltConsumer;

    private OrderCreatedEvent event;

    @BeforeEach
    void setUp() {
        event = new OrderCreatedEvent();
        event.setEventId(UUID.randomUUID());
        event.setCorrelationId("correlation-123");
        event.setOrderId(101L);
    }

    @Test
    void shouldPersistFailedEventWithKafkaMetadata() throws Exception {
        RecordHeaders headers = new RecordHeaders();
        headers.add(
                "kafka_dlt-exception-fqcn",
                "java.lang.RuntimeException".getBytes(StandardCharsets.UTF_8)
        );
        headers.add(
                "kafka_dlt-exception-message",
                "Payment processing failed".getBytes(StandardCharsets.UTF_8)
        );

        ConsumerRecord<String, OrderCreatedEvent> record =
                new ConsumerRecord<>(
                        "order-created.DLT",
                        2,
                        42L,
                        "order-key-101",
                        event
                );

        record.headers().add(headers.lastHeader("kafka_dlt-exception-fqcn"));
        record.headers().add(headers.lastHeader("kafka_dlt-exception-message"));

        when(objectMapper.writeValueAsString(event))
                .thenReturn("{\"orderId\":101}");

        paymentDltConsumer.consumeDlt(event, record);

        verify(deadLetterEventService).recordFailure(
                eq(event),
                eq("order-created.DLT"),
                eq(2),
                eq(42L),
                eq("order-key-101"),
                eq("java.lang.RuntimeException"),
                eq("Payment processing failed"),
                eq("{\"orderId\":101}")
        );
    }

    @Test
    void shouldRejectNullEvent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> paymentDltConsumer.consumeDlt(null, null)
        );

        verifyNoInteractions(deadLetterEventService);
    }

    @Test
    void shouldRejectEventWithoutEventId() {
        event.setEventId(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> paymentDltConsumer.consumeDlt(event, null)
        );

        verifyNoInteractions(deadLetterEventService);
    }
}
