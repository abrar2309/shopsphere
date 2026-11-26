-- Adds optional product color metadata.
-- Existing products are preserved.
ALTER TABLE products
ADD COLUMN IF NOT EXISTS color VARCHAR(40);
