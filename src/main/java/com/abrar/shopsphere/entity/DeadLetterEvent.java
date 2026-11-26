
package com.abrar.shopsphere.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "dead_letter_events",
        indexes = {
                @Index(
                        name = "idx_dlt_status_failed_at",
                        columnList = "status, failed_at"
                )
        }
)
public class DeadLetterEvent {

    public enum FailureStatus {
        FAILED,
        REPLAY_REQUESTED,
        REPLAYED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "partition_number", nullable = false)
    private int partitionNumber;

    @Column(name = "record_offset", nullable = false)
    private long recordOffset;

    @Column(name = "message_key")
    private String messageKey;

    @Column(name = "exception_class")
    private String exceptionClass;

    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FailureStatus status;

    // Public constructor: accessible from the service package.
    public DeadLetterEvent() {
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getPartitionNumber() {
        return partitionNumber;
    }

    public void setPartitionNumber(int partitionNumber) {
        this.partitionNumber = partitionNumber;
    }

    public long getRecordOffset() {
        return recordOffset;
    }

    public void setRecordOffset(long recordOffset) {
        this.recordOffset = recordOffset;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public FailureStatus getStatus() {
        return status;
    }

    public void setStatus(FailureStatus status) {
        this.status = status;
    }
}
