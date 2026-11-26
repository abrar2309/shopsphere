
package com.abrar.shopsphere.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EmbeddedKafka(
        partitions = 1,
        topics = {"retry-test", "retry-test.DLT"}
)
class KafkaRetryDltIntegrationTest {

    @Test
    void failedMessageShouldBeRetriedAndPublishedToDlt(
            EmbeddedKafkaBroker broker) throws Exception {

        Map<String, Object> producerProperties =
                new HashMap<>(KafkaTestUtils.producerProps(broker));

        producerProperties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );
        producerProperties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        DefaultKafkaProducerFactory<Object, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProperties);

        KafkaTemplate<Object, Object> kafkaTemplate =
                new KafkaTemplate<>(producerFactory);

        Map<String, Object> consumerProperties = new HashMap<>();

        consumerProperties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                broker.getBrokersAsString()
        );
        consumerProperties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "retry-test-" + UUID.randomUUID()
        );
        consumerProperties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        consumerProperties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );
        consumerProperties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        consumerProperties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProperties);

        AtomicInteger attempts = new AtomicInteger();

        ContainerProperties containerProperties =
                new ContainerProperties("retry-test");

        containerProperties.setMessageListener(
                (org.springframework.kafka.listener.MessageListener<String, String>)
                        record -> {
                            attempts.incrementAndGet();
                            throw new RuntimeException(
                                    "Intentional integration-test failure"
                            );
                        }
        );

        KafkaMessageListenerContainer<String, String> container =
                new KafkaMessageListenerContainer<>(
                        consumerFactory,
                        containerProperties
                );

        KafkaErrorHandlerConfig errorHandlerConfig =
                new KafkaErrorHandlerConfig();

        container.setCommonErrorHandler(
                errorHandlerConfig.kafkaErrorHandler(kafkaTemplate)
        );

        Map<String, Object> dltConsumerProperties =
                new HashMap<>(consumerProperties);

        dltConsumerProperties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlt-test-" + UUID.randomUUID()
        );

        try (Consumer<String, String> dltConsumer =
                     new KafkaConsumer<>(dltConsumerProperties)) {

            dltConsumer.subscribe(
                    Collections.singletonList("retry-test.DLT")
            );

            container.start();

            assertTrue(
                    container.isRunning(),
                    "The test listener container should start"
            );

            kafkaTemplate.send(
                    "retry-test",
                    "test-event-id",
                    "deliberately-failing-event"
            ).get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> dltRecord = null;

            long deadline = System.nanoTime()
                    + Duration.ofSeconds(25).toNanos();

            while (dltRecord == null
                    && System.nanoTime() < deadline) {

                var records = dltConsumer.poll(
                        Duration.ofMillis(250)
                );

                if (!records.isEmpty()) {
                    dltRecord = records.iterator().next();
                }
            }

            assertNotNull(
                    dltRecord,
                    "Expected failed event in retry-test.DLT"
            );

            assertEquals("test-event-id", dltRecord.key());
            assertEquals(
                    "deliberately-failing-event",
                    dltRecord.value()
            );

            assertEquals(
                    3,
                    attempts.get(),
                    "Expected one initial attempt and two retries"
            );

        } finally {
            container.stop();
            kafkaTemplate.destroy();
            producerFactory.destroy();
        }
    }

    @Test
    void shouldConfigureTwoRetriesWithOneSecondBackoff() {
        FixedBackOff backOff =
                KafkaErrorHandlerConfig.retryBackOff();

        assertEquals(1000L, backOff.getInterval());
        assertEquals(2L, backOff.getMaxAttempts());
    }
}
