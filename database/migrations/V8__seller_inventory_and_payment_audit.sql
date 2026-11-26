-- ShopSphere V8 (corrected): seller inventory + product images + payment audit.
-- Target validated against the local ShopSphere PostgreSQL 18.7 schema.
-- MANUAL MIGRATION: inspect, back up, then apply explicitly to the intended database.
-- This script is transactional and additive. It does not delete or rewrite existing rows.
--
-- Important design choices:
-- * inventory_movements.product_id uses ON DELETE SET NULL and stores product_name_snapshot.
--   Historical movements survive product deletion.
-- * payment_verification_history is protected by a trigger against UPDATE/DELETE.
--   Database owners/superusers can still bypass triggers; this is operational audit
--   protection, not cryptographic/WORM storage.
-- * sales use order_items.price * quantity, allocated to the product's current seller.
--   If seller ownership can change, introduce seller_id snapshots on order_items first.

BEGIN;

-- 1. Multiple product images. products.image_url remains the legacy primary image.
CREATE TABLE IF NOT EXISTS public.product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL
        REFERENCES public.products(id) ON DELETE CASCADE,
    image_url VARCHAR(1000) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_product_images_display_order CHECK (display_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_product_images_product_order
    ON public.product_images(product_id, display_order, id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_images_one_primary
    ON public.product_images(product_id)
    WHERE is_primary = TRUE;

-- 2. Per-product low-stock threshold. Add the constraint only if absent.
ALTER TABLE public.products
    ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER NOT NULL DEFAULT 5;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.products'::regclass
          AND conname = 'ck_products_low_stock_threshold'
    ) THEN
        ALTER TABLE public.products
            ADD CONSTRAINT ck_products_low_stock_threshold
            CHECK (low_stock_threshold >= 0);
    END IF;
END
$$;

-- 3. Inventory ledger. Product ID may become NULL after product deletion,
--    while the name snapshot preserves context for the historical record.
CREATE TABLE IF NOT EXISTS public.inventory_movements (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NULL
        REFERENCES public.products(id) ON DELETE SET NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    seller_id BIGINT NULL
        REFERENCES public.users(id) ON DELETE SET NULL,
    quantity_before INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    quantity_delta INTEGER NOT NULL,
    reason VARCHAR(80) NOT NULL,
    reference_type VARCHAR(40) NULL,
    reference_id VARCHAR(100) NULL,
    actor_user_id BIGINT NULL
        REFERENCES public.users(id) ON DELETE SET NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_movement_quantities CHECK (
        quantity_before >= 0
        AND quantity_after >= 0
        AND quantity_delta = quantity_after - quantity_before
    )
);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_product_created
    ON public.inventory_movements(product_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_seller_created
    ON public.inventory_movements(seller_id, created_at DESC);

-- 4. Capture stock changes. Application services may set transaction-local
--    metadata with set_config('shopsphere.inventory_reason', 'RESTOCK', true), etc.
CREATE OR REPLACE FUNCTION public.shopsphere_capture_inventory_movement()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_reason TEXT;
    v_actor TEXT;
    v_actor_id BIGINT;
    v_reference_type TEXT;
    v_reference_id TEXT;
    v_note TEXT;
BEGIN
    IF NEW.stock IS DISTINCT FROM OLD.stock THEN
        v_reason := COALESCE(
            NULLIF(current_setting('shopsphere.inventory_reason', true), ''),
            'SYSTEM_STOCK_UPDATE'
        );
        v_actor := NULLIF(current_setting('shopsphere.actor_user_id', true), '');
        v_reference_type := NULLIF(current_setting('shopsphere.reference_type', true), '');
        v_reference_id := NULLIF(current_setting('shopsphere.reference_id', true), '');
        v_note := NULLIF(current_setting('shopsphere.inventory_note', true), '');

        v_actor_id := NULL;
        IF v_actor ~ '^[0-9]{1,18}$' THEN
            BEGIN
                v_actor_id := v_actor::BIGINT;
            EXCEPTION WHEN numeric_value_out_of_range THEN
                v_actor_id := NULL;
            END;
        END IF;

        INSERT INTO public.inventory_movements (
            product_id,
            product_name_snapshot,
            seller_id,
            quantity_before,
            quantity_after,
            quantity_delta,
            reason,
            reference_type,
            reference_id,
            actor_user_id,
            note
        ) VALUES (
            NEW.id,
            COALESCE(NEW.name, '[unnamed product]'),
            NEW.seller_id,
            OLD.stock,
            NEW.stock,
            NEW.stock - OLD.stock,
            LEFT(v_reason, 80),
            LEFT(v_reference_type, 40),
            LEFT(v_reference_id, 100),
            v_actor_id,
            LEFT(v_note, 500)
        );
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_products_inventory_movement ON public.products;

CREATE TRIGGER trg_products_inventory_movement
AFTER UPDATE OF stock ON public.products
FOR EACH ROW
EXECUTE FUNCTION public.shopsphere_capture_inventory_movement();

-- 5. Payment verification audit history.
--    Includes initial payment creation and subsequent status/admin verification changes.
CREATE TABLE IF NOT EXISTS public.payment_verification_history (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL
        REFERENCES public.payments(id) ON DELETE RESTRICT,
    order_id BIGINT NULL
        REFERENCES public.orders(id) ON DELETE SET NULL,
    old_status VARCHAR(24) NULL,
    new_status VARCHAR(24) NOT NULL,
    payment_method VARCHAR(32) NULL,
    admin_note TEXT NULL,
    verified_by BIGINT NULL
        REFERENCES public.users(id) ON DELETE SET NULL,
    verified_at TIMESTAMP WITHOUT TIME ZONE NULL,
    changed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operation VARCHAR(10) NOT NULL DEFAULT 'UPDATE'
);

CREATE INDEX IF NOT EXISTS idx_payment_verification_history_payment
    ON public.payment_verification_history(payment_id, changed_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_payment_verification_history_order
    ON public.payment_verification_history(order_id, changed_at DESC, id DESC);

CREATE OR REPLACE FUNCTION public.shopsphere_capture_payment_verification()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO public.payment_verification_history (
            payment_id, order_id, old_status, new_status, payment_method,
            admin_note, verified_by, verified_at, operation
        ) VALUES (
            NEW.id, NEW.order_id, NULL, NEW.status, NEW.payment_method,
            NEW.admin_note, NEW.verified_by, NEW.verified_at, 'INSERT'
        );
        RETURN NEW;
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status
       OR NEW.payment_method IS DISTINCT FROM OLD.payment_method
       OR NEW.admin_note IS DISTINCT FROM OLD.admin_note
       OR NEW.verified_at IS DISTINCT FROM OLD.verified_at
       OR NEW.verified_by IS DISTINCT FROM OLD.verified_by THEN
        INSERT INTO public.payment_verification_history (
            payment_id, order_id, old_status, new_status, payment_method,
            admin_note, verified_by, verified_at, operation
        ) VALUES (
            NEW.id, NEW.order_id, OLD.status, NEW.status, NEW.payment_method,
            NEW.admin_note, NEW.verified_by, NEW.verified_at, 'UPDATE'
        );
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_payments_verification_history ON public.payments;

CREATE TRIGGER trg_payments_verification_history
AFTER INSERT OR UPDATE ON public.payments
FOR EACH ROW
EXECUTE FUNCTION public.shopsphere_capture_payment_verification();

-- Prevent ordinary UPDATE/DELETE operations on audit rows.
-- Table owners/superusers can disable triggers; use restricted application roles.
CREATE OR REPLACE FUNCTION public.shopsphere_reject_payment_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'payment_verification_history is append-only';
END;
$$;

DROP TRIGGER IF EXISTS trg_payment_history_append_only
    ON public.payment_verification_history;

CREATE TRIGGER trg_payment_history_append_only
BEFORE UPDATE OR DELETE ON public.payment_verification_history
FOR EACH ROW
EXECUTE FUNCTION public.shopsphere_reject_payment_history_mutation();

-- 6. Seller inventory summary.
CREATE OR REPLACE VIEW public.seller_inventory_summary AS
SELECT
    p.seller_id,
    COUNT(*) AS product_count,
    COALESCE(SUM(p.stock), 0) AS total_units,
    COALESCE(
        SUM(p.stock::NUMERIC * COALESCE(p.price, 0)::NUMERIC),
        0
    )::NUMERIC(18,2) AS inventory_value,
    COUNT(*) FILTER (WHERE p.stock = 0) AS out_of_stock_products,
    COUNT(*) FILTER (
        WHERE p.stock > 0 AND p.stock <= p.low_stock_threshold
    ) AS low_stock_products
FROM public.products p
GROUP BY p.seller_id;

-- 7. Seller daily sales. The live schema has a unique payment.order_id index,
--    so one successful payment can join each order. Sales are based on the
--    recorded order-item price, not current product price.
--    Date uses payments.created_at as stored (timestamp without time zone).
CREATE OR REPLACE VIEW public.seller_daily_sales AS
SELECT
    p.seller_id,
    pay.created_at::DATE AS sales_date,
    COUNT(DISTINCT o.id) AS paid_orders,
    COALESCE(SUM(COALESCE(oi.quantity, 0)), 0)::BIGINT AS units_sold,
    COALESCE(
        SUM(
            COALESCE(oi.quantity, 0)::NUMERIC
            * COALESCE(oi.price, 0)::NUMERIC
        ),
        0
    )::NUMERIC(18,2) AS gross_sales
FROM public.payments pay
JOIN public.orders o
  ON o.id = pay.order_id
JOIN public.order_items oi
  ON oi.order_id = o.id
JOIN public.products p
  ON p.id = oi.product_id
WHERE UPPER(pay.status) = 'SUCCESS'
GROUP BY p.seller_id, pay.created_at::DATE;

COMMIT;
