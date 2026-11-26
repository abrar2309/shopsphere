CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    sku VARCHAR(80) NOT NULL,
    size VARCHAR(40),
    color VARCHAR(40),
    price DOUBLE PRECISION,
    stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_variant_sku UNIQUE (sku),
    CONSTRAINT ck_product_variant_price CHECK (price IS NULL OR price > 0)
);

CREATE INDEX idx_product_variant_product_active
    ON product_variants(product_id, active);
