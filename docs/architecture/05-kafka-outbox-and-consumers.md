# 05 — Kafka, Transactional Outbox and Consumers

## 1. Why an outbox exists

The order service persists the order and an `OrderCreated` outbox record within the same transactional boundary. This avoids relying on a direct broker send as the only record of an event when the business transaction commits.

## 2. Write and publish path

```mermaid
sequenceDiagram
  participant Order as OrderService
  participant Outbox as OutboxService
  participant DB as PostgreSQL
  participant Publisher as OutboxPublisher
  participant Kafka

  Order->>DB: Save order + items
  Order->>Outbox: saveOrderCreatedEvent(event)
  Outbox->>DB: Insert OutboxEvent (published=false)
  Note over Order,DB: Both writes are in the order transaction
  DB-->>Order: Commit
  loop Scheduled polling (default 5 seconds)
    Publisher->>DB: Claim up to 20 unpublished events
    DB-->>Publisher: Claimed rows + claim token
    Publisher->>Kafka: Send OrderCreated to order-created
    Kafka-->>Publisher: Broker send completion
    Publisher->>DB: Mark publication state / status
  end
```

The publisher is conditional on `shopsphere.outbox.enabled` (default matching property is enabled) and runs on a scheduled fixed delay configured by `shopsphere.outbox.poll-delay-ms` (default 5000 ms).

## 3. Consumer groups

```mermaid
flowchart TB
  TOPIC[("Kafka topic: order-created")]
  TOPIC --> IG["InventoryConsumer\ngroup inventory-group"]
  TOPIC --> PG["PaymentConsumer\ngroup payment-group"]
  TOPIC --> NG["NotificationConsumer\ngroup notification-group"]

  IG --> ILOG["Logs order items and quantities"]
  PG --> CLAIM["EventClaimService / recovery"]
  CLAIM --> IDEMP["EventIdempotencyService"]
  IDEMP --> PROC[("processed_events")]
  CLAIM --> CLAIMDB[("event_claims")]
  PG --> COMPLETE["EventProcessingCompletionService"]
  NG --> USER["UserRepository lookup"]
  USER --> SEND["NotificationSender"]
```

These are independent consumer groups. Each group receives its own subscription stream; one consumer group is not a sequential step after another.

## 4. Consumer responsibilities observed in source

| Consumer | Topic / group | Observed behavior |
|---|---|---|
| `InventoryConsumer` | `order-created` / `inventory-group` | Reads event item data and logs product ID and quantity. The current listener does not adjust stock. |
| `PaymentConsumer` | `order-created` / `payment-group` | Claims event, attempts stale-claim recovery, checks processed-event state, performs simulated payment processing and completes or marks the claim failed. Concurrency configured as 3. |
| `NotificationConsumer` | `order-created` / `notification-group` | Loads customer by ID and sends order-confirmation notification. |

## 5. Idempotency and recovery

Payment consumer collaborators include:
- `EventClaimService`: acquire a tokenized claim and mark completed/failed.
- `EventClaimRecoveryService`: attempt recovery when an initial claim cannot be acquired.
- `EventIdempotencyService`: query and record processed event IDs.
- `EventProcessingCompletionService`: complete processing with claim ownership.

The model is designed to avoid duplicate work and to recover abandoned processing claims. This is application-level idempotency; it should not be presented as a blanket Kafka exactly-once guarantee.

## 6. Failure handling and DLT

The repository includes `PaymentDltConsumer`, `DeadLetterEventService`, `DeadLetterEventRepository` and Kafka error-handler configuration. These are the building blocks for dead-letter processing and operational review. Refer to the actual Kafka config and tests when documenting the configured retry count, backoff, DLT topic name or retention behavior; do not infer those exact values from class names.

## 7. Observability

The order event includes an event ID and correlation ID. Consumers copy the event correlation ID into MDC and log the topic, partition, offset and key. Preserve event IDs and correlation IDs across producer/consumer logs when troubleshooting.
