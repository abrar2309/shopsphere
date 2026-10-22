package com.abrar.shopsphere.consumer;

import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.event.OrderItemEvent;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class InventoryConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    InventoryConsumer.class
            );


    @KafkaListener(
            topics = "order-created",
            groupId = "inventory-group"
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
                    "Inventory consumer received event. " +
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


            for (OrderItemEvent item :
                    event.getItems()) {

                log.info(
                        "Inventory processing item. " +
                                "orderId={}, productId={}, quantity={}",
                        event.getOrderId(),
                        item.getProductId(),
                        item.getQuantity()
                );
            }


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
