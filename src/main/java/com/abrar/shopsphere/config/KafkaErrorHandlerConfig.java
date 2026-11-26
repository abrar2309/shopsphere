
package com.abrar.shopsphere.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
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

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<?, ?> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        KafkaErrorHandlerConfig::resolveDltDestination
                );

        return new DefaultErrorHandler(
                recoverer,
                retryBackOff()
        );
    }

    static FixedBackOff retryBackOff() {
        return new FixedBackOff(1000L, 2L);
    }

    static TopicPartition resolveDltDestination(
            ConsumerRecord<?, ?> record,
            Exception exception) {

        return new TopicPartition(
                record.topic() + ".DLT",
                record.partition()
        );
    }
}
