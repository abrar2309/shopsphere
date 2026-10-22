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
public class NotificationConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    NotificationConsumer.class
            );


    @KafkaListener(
            topics = "order-created",
            groupId = "notification-group"
    )
    public void consumeOrderCreated(
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


            log.info(
                    "Notification consumer received event. " +
                            "eventId={}, correlationId={}, " +
                            "orderId={}, customerId={}, " +
                            "topic={}, partition={}, " +
                            "offset={}, key={}",
                    event.getEventId(),
                    event.getCorrelationId(),
                    event.getOrderId(),
                    event.getCustomerId(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key()
            );


            log.info(
                    "Sending order confirmation notification. " +
                            "orderId={}, customerId={}",
                    event.getOrderId(),
                    event.getCustomerId()
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
