
package com.abrar.shopsphere.consumer;

import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.notification.NotificationSender;
import com.abrar.shopsphere.repository.UserRepository;

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
            LoggerFactory.getLogger(NotificationConsumer.class);

    private final UserRepository users;
    private final NotificationSender notifications;

    public NotificationConsumer(
            UserRepository users,
            NotificationSender notifications) {

        this.users = users;
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "order-created",
            groupId = "notification-group"
    )
    public void consumeOrderCreated(
            OrderCreatedEvent event,
            ConsumerRecord<String, OrderCreatedEvent> record) {

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
                            "topic={}, partition={}, offset={}, key={}",
                    event.getEventId(),
                    event.getCorrelationId(),
                    event.getOrderId(),
                    event.getCustomerId(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key()
            );

            User user = users.findById(event.getCustomerId())
                    .orElse(null);

            if (user == null) {
                log.error(
                        "Cannot send notification: customer not found. " +
                                "customerId={}, orderId={}, eventId={}",
                        event.getCustomerId(),
                        event.getOrderId(),
                        event.getEventId()
                );
                return;
            }

            log.info(
                    "Sending order confirmation notification. " +
                            "orderId={}, customerId={}",
                    event.getOrderId(),
                    event.getCustomerId()
            );

            notifications.sendOrderConfirmation(user, event);

            log.info(
                    "Order confirmation notification processing completed. " +
                            "orderId={}, customerId={}, eventId={}",
                    event.getOrderId(),
                    event.getCustomerId(),
                    event.getEventId()
            );

        } finally {

            if (previousCorrelationId == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put(
                        "correlationId",
                        previousCorrelationId
                );
            }
        }
    }
}
