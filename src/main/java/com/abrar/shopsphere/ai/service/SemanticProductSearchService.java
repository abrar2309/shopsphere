
package com.abrar.shopsphere.ai.service;

import com.abrar.shopsphere.ai.dto.AiProductSearchResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SemanticProductSearchService {

    private final OpenAIEmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    public SemanticProductSearchService(
            OpenAIEmbeddingService embeddingService,
            JdbcTemplate jdbcTemplate) {

        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiProductSearchResponse> search(
            String query,
            int requestedLimit) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Search query cannot be empty."
            );
        }

        String cleanedQuery = query.trim();

        if (cleanedQuery.length() > 500) {
            throw new IllegalArgumentException(
                    "Search query cannot exceed 500 characters."
            );
        }

        int limit = Math.max(
                1,
                Math.min(requestedLimit, 20)
        );

        List<List<Double>> embeddings =
                embeddingService.generateEmbeddings(
                        List.of(cleanedQuery)
                );

        String vector = toVectorString(embeddings.get(0));

        return jdbcTemplate.query(
                """
                SELECT
                    p.id,
                    p.name,
                    p.description,
                    p.price,
                    p.stock,
                    c.name AS category,
                    1 - (pe.embedding <=> ?::vector)
                        AS similarity
                FROM product_embeddings pe
                JOIN products p
                    ON p.id = pe.product_id
                LEFT JOIN categories c
                    ON c.id = p.category_id
                WHERE pe.embedding_model = ?
                ORDER BY pe.embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> new AiProductSearchResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getObject("price", Double.class),
                        rs.getObject("stock", Integer.class),
                        rs.getString("category"),
                        rs.getDouble("similarity")
                ),
                vector,
                embeddingService.getEmbeddingModel(),
                vector,
                limit
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
                        "Invalid embedding value."
                );
            }

            builder.append(value);
        }

        return builder.append("]").toString();
    }
}
