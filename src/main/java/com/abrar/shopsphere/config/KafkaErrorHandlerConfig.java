package com.abrar.shopsphere.config;

import com.abrar.shopsphere.event.OrderCreatedEvent;

import org.apache.kafka.common.TopicPartition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true"
)
public class KafkaErrorHandlerConfig {

    private static final Logger log =
            LoggerFactory.getLogger(
                    KafkaErrorHandlerConfig.class
            );

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> {

                            log.error(
                                    "Kafka message failed after retries. " +
                                            "Sending event to DLT. " +
                                            "topic={}, partition={}, offset={}, key={}, error={}",
                                    record.topic(),
                                    record.partition(),
                                    record.offset(),
                                    record.key(),
                                    exception.getMessage()
                            );

                            return new TopicPartition(
                                    record.topic() + ".DLT",
                                    record.partition()
                            );
                        }
                );

        FixedBackOff backOff =
                new FixedBackOff(
                        2000L,
                        2L
                );

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        backOff
                );

        errorHandler.setRetryListeners(
                (record, exception, deliveryAttempt) -> {

                    log.warn(
                            "Kafka retry attempt. " +
                                    "topic={}, partition={}, offset={}, " +
                                    "key={}, attempt={}, error={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            record.key(),
                            deliveryAttempt,
                            exception.getMessage()
                    );
                }
        );

        return errorHandler;
    }
}