CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS product_embeddings_bge (
    product_id BIGINT PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    embedding VECTOR(384) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_embeddings_bge_model
    ON product_embeddings_bge (embedding_model);

CREATE INDEX IF NOT EXISTS idx_product_embeddings_bge_hnsw
    ON product_embeddings_bge
    USING hnsw (embedding vector_cosine_ops);