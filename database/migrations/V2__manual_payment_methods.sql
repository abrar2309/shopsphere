ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS upi_transaction_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS admin_note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS verified_by BIGINT;

CREATE INDEX IF NOT EXISTS idx_payments_status_method
    ON payments(status, payment_method);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_upi_transaction
    ON payments(upi_transaction_id)
    WHERE upi_transaction_id IS NOT NULL;