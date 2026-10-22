package com.abrar.shopsphere.config;

import com.abrar.shopsphere.event.OrderCreatedEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true"
)
public class KafkaConsumerConfig {

    private final CommonErrorHandler kafkaErrorHandler;

    @Value("${app.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    public KafkaConsumerConfig(
            CommonErrorHandler kafkaErrorHandler) {

        this.kafkaErrorHandler = kafkaErrorHandler;
    }

    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> consumerFactory() {

        JsonDeserializer<OrderCreatedEvent> jsonDeserializer =
                new JsonDeserializer<>(
                        OrderCreatedEvent.class
                );

        jsonDeserializer.addTrustedPackages(
                "com.abrar.shopsphere.event"
        );

        Map<String, Object> properties =
                new HashMap<>();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class
        );

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                jsonDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<
            String,
            OrderCreatedEvent> kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<
                String,
                OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                consumerFactory()
        );

        factory.setCommonErrorHandler(
                kafkaErrorHandler
        );

        return factory;
    }
}