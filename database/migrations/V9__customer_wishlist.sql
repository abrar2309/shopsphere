CREATE TABLE IF NOT EXISTS wishlist_items (
                                              id BIGSERIAL PRIMARY KEY,
                                              user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_wishlist_user_product
    UNIQUE (user_id, product_id)
    );

CREATE INDEX IF NOT EXISTS idx_wishlist_user_created
    ON wishlist_items(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wishlist_product
    ON wishlist_items(product_id);