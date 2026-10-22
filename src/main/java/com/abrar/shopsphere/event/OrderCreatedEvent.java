package com.abrar.shopsphere.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class OrderCreatedEvent {

    private UUID eventId;

    private String correlationId;

    private Long orderId;

    private Long customerId;

    private Double totalAmount;

    private String status;

    private LocalDateTime occurredAt;

    private List<OrderItemEvent> items;


    public OrderCreatedEvent() {
    }


    public OrderCreatedEvent(
            UUID eventId,
            String correlationId,
            Long orderId,
            Long customerId,
            Double totalAmount,
            String status,
            LocalDateTime occurredAt,
            List<OrderItemEvent> items) {

        this.eventId = eventId;

        this.correlationId =
                correlationId;

        this.orderId =
                orderId;

        this.customerId =
                customerId;

        this.totalAmount =
                totalAmount;

        this.status =
                status;

        this.occurredAt =
                occurredAt;

        this.items =
                items;
    }


    public UUID getEventId() {
        return eventId;
    }


    public void setEventId(
            UUID eventId) {

        this.eventId =
                eventId;
    }


    public String getCorrelationId() {
        return correlationId;
    }


    public void setCorrelationId(
            String correlationId) {

        this.correlationId =
                correlationId;
    }


    public Long getOrderId() {
        return orderId;
    }


    public void setOrderId(
            Long orderId) {

        this.orderId =
                orderId;
    }


    public Long getCustomerId() {
        return customerId;
    }


    public void setCustomerId(
            Long customerId) {

        this.customerId =
                customerId;
    }


    public Double getTotalAmount() {
        return totalAmount;
    }


    public void setTotalAmount(
            Double totalAmount) {

        this.totalAmount =
                totalAmount;
    }


    public String getStatus() {
        return status;
    }


    public void setStatus(
            String status) {

        this.status =
                status;
    }


    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }


    public void setOccurredAt(
            LocalDateTime occurredAt) {

        this.occurredAt =
                occurredAt;
    }


    public List<OrderItemEvent> getItems() {
        return items;
    }


    public void setItems(
            List<OrderItemEvent> items) {

        this.items =
                items;
    }
}