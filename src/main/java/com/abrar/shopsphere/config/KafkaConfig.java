package com.abrar.shopsphere.config;

import org.apache.kafka.clients.admin.NewTopic;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true"
)
public class KafkaConfig {

    @Bean
    public NewTopic orderCreatedTopic() {

        return TopicBuilder
                .name("order-created")
                .partitions(3)
                .replicas(1)
                .build();
    }
}