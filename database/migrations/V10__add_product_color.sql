-- Optional seller-provided product colour metadata.
-- Verify the selected database before applying this migration.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS color VARCHAR(40);

COMMENT ON COLUMN products.color IS
    'Optional seller-provided product colour; NULL means not specified.';