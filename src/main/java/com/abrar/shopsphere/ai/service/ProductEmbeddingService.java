
package com.abrar.shopsphere.ai.service;

import com.abrar.shopsphere.ai.dto.EmbeddingIndexResponse;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.repository.ProductRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class ProductEmbeddingService {

    private static final int BATCH_SIZE = 50;

    private final ProductRepository productRepository;
    private final OpenAIEmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    public ProductEmbeddingService(
            ProductRepository productRepository,
            OpenAIEmbeddingService embeddingService,
            JdbcTemplate jdbcTemplate) {

        this.productRepository = productRepository;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public EmbeddingIndexResponse indexAllProducts() {

        List<Product> products =
                productRepository.findAllWithCategoryAndSeller();

        int indexed = 0;
        int skipped = 0;

        for (int start = 0;
             start < products.size();
             start += BATCH_SIZE) {

            int end = Math.min(
                    start + BATCH_SIZE,
                    products.size()
            );

            List<Product> batch =
                    products.subList(start, end);

            List<Product> productsToIndex = new ArrayList<>();
            List<String> textsToEmbed = new ArrayList<>();
            List<String> hashes = new ArrayList<>();

            for (Product product : batch) {

                String content = buildProductText(product);
                String hash = sha256(content);

                if (isAlreadyIndexed(product.getId(), hash)) {
                    skipped++;
                    continue;
                }

                productsToIndex.add(product);
                textsToEmbed.add(content);
                hashes.add(hash);
            }

            if (productsToIndex.isEmpty()) {
                continue;
            }

            List<List<Double>> vectors =
                    embeddingService.generateEmbeddings(
                            textsToEmbed
                    );

            for (int i = 0;
                 i < productsToIndex.size();
                 i++) {

                saveEmbedding(
                        productsToIndex.get(i).getId(),
                        vectors.get(i),
                        hashes.get(i),
                        embeddingService.getEmbeddingModel()
                );

                indexed++;
            }
        }

        return new EmbeddingIndexResponse(
                products.size(),
                indexed,
                skipped,
                embeddingService.getEmbeddingModel(),
                "Product embedding indexing completed."
        );
    }

    private String buildProductText(Product product) {

        String categoryName = "Uncategorized";

        if (product.getCategory() != null
                && product.getCategory().getName() != null) {

            categoryName = product.getCategory().getName();
        }

        return """
                Product name: %s
                Product description: %s
                Product category: %s
                """.formatted(
                product.getName(),
                product.getDescription(),
                categoryName
        ).trim();
    }

    private boolean isAlreadyIndexed(
            Long productId,
            String hash) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM product_embeddings
                WHERE product_id = ?
                  AND content_hash = ?
                  AND embedding_model = ?
                """,
                Integer.class,
                productId,
                hash,
                embeddingService.getEmbeddingModel()
        );

        return count != null && count > 0;
    }

    private void saveEmbedding(
            Long productId,
            List<Double> vector,
            String hash,
            String model) {

        String vectorValue = toVectorString(vector);

        jdbcTemplate.update(
                """
                INSERT INTO product_embeddings (
                    product_id,
                    embedding,
                    content_hash,
                    embedding_model,
                    updated_at
                )
                VALUES (?, ?::vector, ?, ?, NOW())
                ON CONFLICT (product_id)
                DO UPDATE SET
                    embedding = EXCLUDED.embedding,
                    content_hash = EXCLUDED.content_hash,
                    embedding_model = EXCLUDED.embedding_model,
                    updated_at = NOW()
                """,
                productId,
                vectorValue,
                hash,
                model
        );
    }

    private String toVectorString(List<Double> vector) {

        if (vector.size() != 1536) {
            throw new IllegalArgumentException(
                    "Expected 1536 vector dimensions."
            );
        }

        StringBuilder builder = new StringBuilder("[");

        for (int i = 0; i < vector.size(); i++) {

            if (i > 0) {
                builder.append(",");
            }

            double value = vector.get(i);

            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Embedding contains invalid number."
                );
            }

            builder.append(value);
        }

        return builder.append("]").toString();
    }

    private String sha256(String content) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    content.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }
}
