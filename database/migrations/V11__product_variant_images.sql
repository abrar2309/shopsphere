-- ShopSphere V11: multiple images attached to a product variant.
CREATE TABLE IF NOT EXISTS product_variant_images (
    id BIGSERIAL PRIMARY KEY,
    variant_id BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_variant_images_variant_order
    ON product_variant_images(variant_id, display_order, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_variant_image_primary
    ON product_variant_images(variant_id) WHERE is_primary = TRUE;