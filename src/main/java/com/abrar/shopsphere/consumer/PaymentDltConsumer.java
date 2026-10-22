package com.abrar.shopsphere.consumer;

import com.abrar.shopsphere.event.OrderCreatedEvent;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class PaymentDltConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PaymentDltConsumer.class
            );


    @KafkaListener(
            topics = "order-created.DLT",
            groupId = "payment-dlt-group"
    )
    public void consumeDlt(
            OrderCreatedEvent event,
            ConsumerRecord<
                    String,
                    OrderCreatedEvent> record) {


        String previousCorrelationId =
                MDC.get("correlationId");


        try {

            if (event.getCorrelationId() != null) {

                MDC.put(
                        "correlationId",
                        event.getCorrelationId()
                );
            }


            log.error(
                    "Payment event received in DLT. " +
                            "eventId={}, correlationId={}, " +
                            "orderId={}, topic={}, partition={}, " +
                            "offset={}, key={}",
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

                MDC.remove(
                        "correlationId"
                );

            } else {

                MDC.put(
                        "correlationId",
                        previousCorrelationId
                );
            }
        }
    }
}
