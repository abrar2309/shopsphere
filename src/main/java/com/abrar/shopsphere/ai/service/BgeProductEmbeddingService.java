
package com.abrar.shopsphere.ai.service;

import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.Category;
import com.abrar.shopsphere.repository.ProductRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class BgeProductEmbeddingService {

    private static final int BATCH_SIZE = 32;

    private final ProductRepository productRepository;
    private final BgeEmbeddingClient embeddingClient;
    private final JdbcTemplate jdbcTemplate;

    public BgeProductEmbeddingService(
            ProductRepository productRepository,
            BgeEmbeddingClient embeddingClient,
            JdbcTemplate jdbcTemplate) {

        this.productRepository = productRepository;
        this.embeddingClient = embeddingClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public EmbeddingIndexResult indexAllProducts() {

        List<Product> products =
                productRepository.findAllWithCategoryAndSeller();

        int indexed = 0;
        int skipped = 0;

        for (int start = 0;
             start < products.size();
             start += BATCH_SIZE) {

            List<Product> batch = products.subList(
                    start,
                    Math.min(start + BATCH_SIZE, products.size())
            );

            List<ProductDocument> changed = new ArrayList<>();

            for (Product product : batch) {

                String content = buildProductText(product);
                String hash = sha256(content);

                String existingHash = findExistingHash(product.getId());

                if (hash.equals(existingHash)) {
                    skipped++;
                } else {
                    changed.add(
                            new ProductDocument(
                                    product.getId(),
                                    content,
                                    hash
                            )
                    );
                }
            }

            if (changed.isEmpty()) {
                continue;
            }

            List<String> texts = changed.stream()
                    .map(ProductDocument::text)
                    .toList();

            List<List<Double>> vectors =
                    embeddingClient.generateDocumentEmbeddings(texts);

            for (int i = 0; i < changed.size(); i++) {

                ProductDocument document = changed.get(i);

                String vector = toVectorString(vectors.get(i));

                jdbcTemplate.update("""
                        INSERT INTO product_embeddings_bge
                        (
                            product_id,
                            embedding,
                            content_hash,
                            embedding_model,
                            updated_at
                        )
                        VALUES (?, ?::vector, ?, ?, CURRENT_TIMESTAMP)

                        ON CONFLICT (product_id)
                        DO UPDATE SET
                            embedding = EXCLUDED.embedding,
                            content_hash = EXCLUDED.content_hash,
                            embedding_model = EXCLUDED.embedding_model,
                            updated_at = CURRENT_TIMESTAMP
                        """,

                        document.productId(),
                        vector,
                        document.contentHash(),
                        embeddingClient.getModelName()
                );

                indexed++;
            }
        }

        return new EmbeddingIndexResult(
                products.size(),
                indexed,
                skipped,
                embeddingClient.getModelName(),
                "BGE indexing completed."
        );
    }

    private String findExistingHash(Long productId) {

        List<String> results = jdbcTemplate.query(
                """
                SELECT content_hash
                FROM product_embeddings_bge
                WHERE product_id = ?
                  AND embedding_model = ?
                """,

                (rs, rowNum) -> rs.getString("content_hash"),

                productId,
                embeddingClient.getModelName()
        );

        return results.isEmpty() ? null : results.get(0);
    }

    private String buildProductText(Product product) {

        Category category = product.getCategory();

        String categoryName = category == null
                ? ""
                : category.getName();

        return """
                Product: %s
                Colour: %s
                Description: %s
                Category: %s
                """.formatted(
                safe(product.getName()),
                safe(product.getColor()),
                safe(product.getDescription()),
                safe(categoryName)
        ).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String content) {

        try {

            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Could not calculate product hash.",
                    exception
            );
        }
    }

    private String toVectorString(List<Double> vector) {

        if (vector == null ||
                vector.size() != embeddingClient.getDimensions()) {

            throw new IllegalArgumentException(
                    "Expected 384-dimensional vector."
            );
        }

        StringBuilder builder = new StringBuilder("[");

        for (int i = 0; i < vector.size(); i++) {

            Double value = vector.get(i);

            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Invalid embedding value."
                );
            }

            if (i > 0) {
                builder.append(",");
            }

            builder.append(value);
        }

        return builder.append("]").toString();
    }

    public record EmbeddingIndexResult(
            int totalProducts,
            int indexedProducts,
            int skippedProducts,
            String model,
            String message) {
    }

    private record ProductDocument(
            Long productId,
            String text,
            String contentHash) {
    }
}
