-- ShopSphere simulated payment records.
-- Apply this migration to the existing shopsphere database before deploying
-- the application build that includes the Payment entity. This is additive;
-- it does not modify or delete existing customer, product, order or address data.

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    payment_reference VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(24) NOT NULL,
    payment_method VARCHAR(32) NOT NULL DEFAULT 'SIMULATED',
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_order UNIQUE (order_id),
    CONSTRAINT uk_payment_reference UNIQUE (payment_reference),
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT ck_payment_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILURE'))
);

CREATE INDEX IF NOT EXISTS idx_payment_customer_created
    ON payments(customer_id, created_at DESC);
