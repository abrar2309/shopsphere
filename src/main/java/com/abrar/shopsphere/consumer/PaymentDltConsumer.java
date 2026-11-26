
package com.abrar.shopsphere.consumer;

import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.service.DeadLetterEventService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true"
)
public class PaymentDltConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentDltConsumer.class);

    private static final String DLT_EXCEPTION_CLASS_HEADER =
            "kafka_dlt-exception-fqcn";

    private static final String DLT_EXCEPTION_MESSAGE_HEADER =
            "kafka_dlt-exception-message";

    private final DeadLetterEventService deadLetterEventService;
    private final ObjectMapper objectMapper;

    public PaymentDltConsumer(
            DeadLetterEventService deadLetterEventService,
            ObjectMapper objectMapper) {

        this.deadLetterEventService = deadLetterEventService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order-created.DLT",
            groupId = "payment-dlt-group"
    )
    public void consumeDlt(
            OrderCreatedEvent event,
            ConsumerRecord<String, OrderCreatedEvent> record)
            throws JsonProcessingException {

        String previousCorrelationId = MDC.get("correlationId");

        try {
            if (event != null && event.getCorrelationId() != null) {
                MDC.put("correlationId", event.getCorrelationId());
            }

            if (event == null || event.getEventId() == null) {
                throw new IllegalArgumentException(
                        "Cannot persist DLT message: event or event ID is missing"
                );
            }

            String payload = objectMapper.writeValueAsString(event);

            String exceptionClass = readHeader(
                    record,
                    DLT_EXCEPTION_CLASS_HEADER
            );

            String exceptionMessage = readHeader(
                    record,
                    DLT_EXCEPTION_MESSAGE_HEADER
            );

            deadLetterEventService.recordFailure(
                    event,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    exceptionClass,
                    exceptionMessage,
                    payload
            );

            log.error(
                    "Payment event persisted to DLT failure records. " +
                            "eventId={}, correlationId={}, orderId={}, " +
                            "topic={}, partition={}, offset={}, key={}",
                    event.getEventId(),
                    event.getCorrelationId(),
                    event.getOrderId(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key()
            );

        } finally {
            if (previousCorrelationId == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previousCorrelationId);
            }
        }
    }

    private String readHeader(
            ConsumerRecord<String, OrderCreatedEvent> record,
            String headerName) {

        Header header = record.headers().lastHeader(headerName);

        if (header == null || header.value() == null) {
            return null;
        }

        return new String(
                header.value(),
                StandardCharsets.UTF_8
        );
    }
}
