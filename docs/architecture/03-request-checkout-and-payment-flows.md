# 03 — Request, Checkout and Payment Flows

## 1. Standard synchronous API request

1. React page calls a function from `shopsphere-ui/src/api/`.
2. The API module uses the shared frontend HTTP client.
3. Spring Security evaluates the request; JWT authentication is applied where required.
4. Spring MVC dispatches to a controller.
5. The controller validates request DTOs and calls a service.
6. The service applies business rules and transaction boundaries.
7. Repositories/JdbcTemplate access PostgreSQL.
8. The service maps results to response DTOs.

## 2. Cart operations

`CartController` exposes:
- `GET /cart`
- `POST /cart/items`
- `PUT /cart/items/{productId}`
- `DELETE /cart/items/{productId}`
- `DELETE /cart`

The controller delegates these operations to `CartService`. The `Cart` entity has a one-to-one relation with `User`; `CartItem` belongs to a cart and references a product. The database mapping enforces uniqueness for a user's cart and for a product within one cart.

## 3. Order creation sequence

The sequence below follows `OrderController.createMyOrder` and `OrderService.createOrderForAuthenticatedUser/createOrder`.

```mermaid
sequenceDiagram
  actor Customer
  participant FE as React
  participant OC as OrderController
  participant OS as OrderService
  participant UR as UserRepository
  participant AR as AddressRepository
  participant RL as RateLimitService
  participant PR as ProductRepository
  participant OR as OrderRepository
  participant OUT as OutboxService
  participant DB as PostgreSQL

  Customer->>FE: Confirm checkout
  FE->>OC: POST /orders (JWT + OrderRequestDTO)
  OC->>OS: createOrderForAuthenticatedUser(request)
  OS->>UR: Resolve authenticated customer
  UR->>DB: Find user by authenticated email
  DB-->>UR: User
  OS->>AR: findByIdAndUserId(addressId, userId)
  AR->>DB: Verify address ownership
  DB-->>AR: Address
  OS->>RL: Check per-customer order-create limit
  loop Each requested item
    OS->>PR: findByIdForUpdate(productId)
    PR->>DB: Pessimistic row lock / read product
    DB-->>PR: Product + available stock
    OS->>OS: Validate quantity and stock; decrement stock
  end
  OS->>OR: save(order with items + address snapshot)
  OR->>DB: Persist order graph
  OS->>OUT: saveOrderCreatedEvent(event)
  OUT->>DB: Insert outbox row
  Note over DB,OUT: Same transaction boundary as order creation
  DB-->>OS: Commit
  OS-->>OC: OrderResponseDTO
  OC-->>FE: 201 Created
  FE-->>Customer: Show created order
```

### Important transaction and concurrency details

- The order creation service method is annotated `@Transactional`.
- Address ownership is checked before items are processed.
- Each product is loaded with `ProductRepository.findByIdForUpdate`; the repository declares a pessimistic write lock for that lookup.
- The service checks stock while holding that row lock and decrements the product stock.
- Order and order items are saved before the outbox row is written.
- The order-created event is written to the outbox from the same transaction, so the business write and event record share a commit boundary.

The diagram shows the service-level orchestration. Exact SQL execution ordering is managed by JPA flush/transaction semantics and may differ from the simplified sequence.

## 4. Payment API and state flow

The payment endpoints are separate from order creation:
- `POST /orders/{orderId}/payment/manual`
- `POST /orders/{orderId}/payment/simulate`
- `GET /orders/{orderId}/payment`
- `GET /admin/payments/pending`
- `POST /admin/payments/{paymentId}/verify`

```mermaid
sequenceDiagram
  actor Customer
  actor Admin
  participant Controller as PaymentController
  participant Service as PaymentService
  participant OrderRepo as OrderRepository
  participant ProductRepo as ProductRepository
  participant PaymentRepo as PaymentRepository
  participant DB as PostgreSQL

  Customer->>Controller: POST payment/manual or payment/simulate
  Controller->>Service: Create payment request
  Service->>OrderRepo: findByIdAndUserIdForUpdate
  OrderRepo->>DB: Lock owned order
  DB-->>OrderRepo: Order
  Service->>PaymentRepo: Check idempotency key / existing payment
  PaymentRepo->>DB: Lookup
  DB-->>PaymentRepo: Existing or absent
  Service->>PaymentRepo: Save payment record
  PaymentRepo->>DB: Persist state
  DB-->>Service: Payment
  Service-->>Controller: PaymentResponseDTO
  Controller-->>Customer: Payment result

  Admin->>Controller: POST admin/payments/{id}/verify
  Controller->>Service: verifyPayment
  Service->>PaymentRepo: Load pending payment
  Service->>OrderRepo: Lock related order
  alt Admin approves
    Service->>DB: Payment SUCCESS; order CONFIRMED
  else Admin rejects
    loop Ordered product items
      Service->>ProductRepo: findByIdForUpdate
      ProductRepo->>DB: Lock product row
      Service->>DB: Restore reserved stock
    end
    Service->>DB: Payment FAILURE; order PAYMENT_FAILED
  end
  Service-->>Controller: Updated payment DTO
  Controller-->>Admin: Result
```

### Payment modes represented in code

| Mode | Behavior |
|---|---|
| Manual UPI | Requires a UPI transaction ID; stores a PENDING payment with an idempotency key |
| COD | Stores a PENDING payment and moves order to COD_PENDING |
| Simulated | Accepts SUCCESS/FAILURE test outcome; updates order and restores reserved stock on failure |
| Admin verification | Approves or rejects pending UPI/COD records; rejection restores reserved stock |

Do not describe the code as a completed live Razorpay gateway integration without verifying the provider request/callback implementation. The SDK is present in the build file, but the inspected `PaymentService` flow described here is manual/simulated.
