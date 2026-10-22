package com.abrar.shopsphere.service;

import com.abrar.shopsphere.event.OrderCreatedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(
                    OrderEventPublisher.class
            );

    private final KafkaTemplate<String, OrderCreatedEvent>
            kafkaTemplate;

    public OrderEventPublisher(
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {

        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(
            OrderCreatedEvent event) {

        kafkaTemplate.send(
                "order-created",
                event.getOrderId().toString(),
                event
        );

        log.info(
                "OrderCreated event published. " +
                        "eventId={}, orderId={}",
                event.getEventId(),
                event.getOrderId()
        );
    }
}