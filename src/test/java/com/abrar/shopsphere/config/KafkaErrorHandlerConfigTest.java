
package com.abrar.shopsphere.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.FixedBackOff;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaErrorHandlerConfigTest {

    @Test
    void shouldConfigureTwoRetriesWithOneSecondBackoff() {

        FixedBackOff backOff =
                KafkaErrorHandlerConfig.retryBackOff();

        assertEquals(1000L, backOff.getInterval());
        assertEquals(2L, backOff.getMaxAttempts());
    }

    @Test
    void shouldResolveDeadLetterTopicWithOriginalPartition() {

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "orders",
                        2,
                        10L,
                        "order-123",
                        "test-event"
                );

        TopicPartition destination =
                KafkaErrorHandlerConfig.resolveDltDestination(
                        record,
                        new RuntimeException("Test failure")
                );

        assertEquals("orders.DLT", destination.topic());
        assertEquals(2, destination.partition());
    }
}
