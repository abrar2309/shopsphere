package com.abrar.shopsphere.ai.service;

import com.abrar.shopsphere.ai.dto.AiProductSearchResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BgeSemanticProductSearchService {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "below", "for", "in", "less",
            "of", "on", "or", "the", "to", "under", "upto", "with"
    );

    private static final Map<String, List<String>> PRODUCT_TYPES = Map.ofEntries(
            Map.entry("headphones", List.of("headphone", "earbud", "headset")),
            Map.entry("backpacks", List.of("backpack", "rucksack")),
            Map.entry("jackets", List.of("jacket", "coat", "blazer", "windbreaker")),
            Map.entry("shoes", List.of("shoe", "sneaker", "trainer", "boot", "sandal")),
            Map.entry("shirts", List.of("shirt", "t-shirt", "tee", "polo")),
            Map.entry("hoodies", List.of("hoodie", "sweatshirt")),
            Map.entry("jeans", List.of("jean", "trouser", "chino", "legging")),
            Map.entry("speakers", List.of("speaker")),
            Map.entry("watches", List.of("watch")),
            Map.entry("wallets", List.of("wallet")),
            Map.entry("sunglasses", List.of("sunglass")),
            Map.entry("laptops", List.of("laptop", "notebook"))
    );

    private static final Map<String, List<String>> COLOURS = Map.ofEntries(
            Map.entry("red", List.of("red")),
            Map.entry("blue", List.of("blue", "navy")),
            Map.entry("black", List.of("black")),
            Map.entry("white", List.of("white")),
            Map.entry("green", List.of("green", "olive")),
            Map.entry("yellow", List.of("yellow")),
            Map.entry("pink", List.of("pink")),
            Map.entry("purple", List.of("purple", "violet")),
            Map.entry("brown", List.of("brown", "tan")),
            Map.entry("grey", List.of("grey", "gray", "charcoal")),
            Map.entry("orange", List.of("orange"))
    );

    private final BgeEmbeddingClient embeddingClient;
    private final JdbcTemplate jdbcTemplate;

    public BgeSemanticProductSearchService(
            BgeEmbeddingClient embeddingClient,
            JdbcTemplate jdbcTemplate) {
        this.embeddingClient = embeddingClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiProductSearchResponse> search(String query, int requestedLimit) {
        return search(query, requestedLimit, null, null, null);
    }

    public List<AiProductSearchResponse> search(
            String query, int requestedLimit, String category) {
        return search(query, requestedLimit, category, null, null);
    }

    public List<AiProductSearchResponse> search(
            String query,
            int requestedLimit,
            String category,
            Double minPrice,
            Double maxPrice) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query cannot be empty.");
        }

        String cleanedQuery = query.trim();
        if (cleanedQuery.length() > 500) {
            throw new IllegalArgumentException("Search query cannot exceed 500 characters.");
        }

        String cleanedCategory = category == null || category.isBlank()
                ? null : category.trim();
        if (cleanedCategory != null && cleanedCategory.length() > 100) {
            throw new IllegalArgumentException("Category cannot exceed 100 characters.");
        }
        if (minPrice != null && (!Double.isFinite(minPrice) || minPrice < 0)) {
            throw new IllegalArgumentException("Minimum price must be a valid non-negative number.");
        }
        if (maxPrice != null && (!Double.isFinite(maxPrice) || maxPrice < 0)) {
            throw new IllegalArgumentException("Maximum price must be a valid non-negative number.");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("Minimum price cannot exceed maximum price.");
        }

        int limit = Math.max(1, Math.min(requestedLimit, 50));
        String normalized = normalize(cleanedQuery);

        if (containsAny(normalized, "backpacking")
                && !containsAny(normalized, "backpack", "rucksack")) {
            return List.of();
        }
        List<String> productTerms = findProductType(normalized);
        List<String> colourTerms = findColour(normalized);
        List<String> sizeTerms = findSize(normalized);

        // If an explicit colour or product type is requested, it is a hard
        // catalogue constraint. BGE may rank within the matching set only.
        try {
            List<Double> queryEmbedding =
                    embeddingClient.generateQueryEmbedding(cleanedQuery);
            String vector = toVectorString(queryEmbedding);
            List<AiProductSearchResponse> semanticResults = semanticSearch(
                    vector, cleanedCategory, minPrice, maxPrice,
                    productTerms, colourTerms, sizeTerms, limit);

            if (!semanticResults.isEmpty()) {
                return semanticResults;
            }

            return lexicalFallback(
                    cleanedQuery, cleanedCategory, minPrice, maxPrice,
                    productTerms, colourTerms, sizeTerms, limit);
        } catch (RuntimeException ignored) {
            // Keep the catalogue keyword fallback available if BGE is down.
            return lexicalFallback(
                    cleanedQuery, cleanedCategory, minPrice, maxPrice,
                    productTerms, colourTerms, sizeTerms, limit);
        }
    }

    private List<AiProductSearchResponse> semanticSearch(
            String vector,
            String category,
            Double minPrice,
            Double maxPrice,
            List<String> productTerms,
            List<String> colourTerms,
            List<String> sizeTerms,
            int limit) {

        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.name, p.description, p.price, p.stock,
                       c.name AS category,
                       CASE
                           WHEN pe.embedding IS NULL THEN 0.0
                           ELSE 1 - (pe.embedding <=> ?::vector)
                       END AS similarity
                FROM products p
                LEFT JOIN product_embeddings_bge pe
                       ON pe.product_id = p.id
                      AND pe.embedding_model = ?
                LEFT JOIN categories c ON c.id = p.category_id
                WHERE (
                    p.stock > 0
                    OR EXISTS (
                        SELECT 1
                        FROM product_variants pv_stock
                        WHERE pv_stock.product_id = p.id
                          AND pv_stock.active = TRUE
                          AND pv_stock.stock > 0
                    )
                )
                """);

        List<Object> args = new ArrayList<>();
        args.add(vector);
        args.add(embeddingClient.getModelName());

        // Semantic results must always have a valid BGE embedding.
        sql.append(" AND pe.embedding IS NOT NULL");

        // Reject weak matches for unrestricted natural-language searches.
        // Explicit product, colour and size searches retain their variant filters.
        if (productTerms.isEmpty() && colourTerms.isEmpty() && sizeTerms.isEmpty()) {
            sql.append(" AND (1 - (pe.embedding <=> ?::vector)) >= ?");
            args.add(vector);
            args.add(0.60d);
        }

        appendOptionalFilters(sql, args, category, minPrice, maxPrice,
                productTerms, colourTerms, sizeTerms, false);

        sql.append(" ORDER BY pe.embedding <=> ?::vector NULLS LAST, p.id DESC LIMIT ?");
        args.add(vector);
        args.add(limit);

        List<AiProductSearchResponse> results = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new AiProductSearchResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getObject("price", Double.class),
                        rs.getObject("stock", Integer.class),
                        rs.getString("category"),
                        rs.getDouble("similarity")
                ),
                args.toArray()
        );

        if (productTerms.isEmpty() && colourTerms.isEmpty() && sizeTerms.isEmpty()) {
            return results.stream()
                    .filter(result -> result.similarity() >= 0.60)
                    .toList();
        }

        return results;
    }

    private List<AiProductSearchResponse> lexicalFallback(
            String query,
            String category,
            Double minPrice,
            Double maxPrice,
            List<String> productTerms,
            List<String> colourTerms,
            List<String> sizeTerms,
            int limit) {

        List<String> tokens = java.util.Arrays.stream(
                        normalize(query).split("\\s+"))
                .filter(token -> !token.isBlank() && !STOP_WORDS.contains(token))
                .distinct()
                .limit(8)
                .toList();

        String searchable = "LOWER(CONCAT_WS(' ', p.name, p.description, p.color, c.name))";
        String scoreExpression = tokens.isEmpty() ? "0" : tokens.stream()
                .map(token -> "CASE WHEN " + searchable + " LIKE ? THEN 1 ELSE 0 END")
                .reduce((a, b) -> a + " + " + b).orElse("0");

        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.name, p.description, p.price, p.stock,
                       c.name AS category,
                       (%s)::double precision AS similarity
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                WHERE (
                    p.stock > 0
                    OR EXISTS (
                        SELECT 1
                        FROM product_variants pv_stock
                        WHERE pv_stock.product_id = p.id
                          AND pv_stock.active = TRUE
                          AND pv_stock.stock > 0
                    )
                )
                """.formatted(scoreExpression));

        List<Object> args = new ArrayList<>();
        for (String token : tokens) args.add("%" + token + "%");

        appendOptionalFilters(sql, args, category, minPrice, maxPrice,
                productTerms, colourTerms, sizeTerms, true);

        if (productTerms.isEmpty() && colourTerms.isEmpty() && sizeTerms.isEmpty()
                && !tokens.isEmpty()) {
            sql.append(" AND (").append(scoreExpression).append(") > 0");
            for (String token : tokens) {
                args.add("%" + token + "%");
            }
        }

        sql.append(" ORDER BY similarity DESC, p.id DESC LIMIT ?");
        args.add(limit);

        List<AiProductSearchResponse> results = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new AiProductSearchResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getObject("price", Double.class),
                        rs.getObject("stock", Integer.class),
                        rs.getString("category"),
                        rs.getDouble("similarity")
                ),
                args.toArray()
        );

        if (productTerms.isEmpty() && colourTerms.isEmpty() && sizeTerms.isEmpty()) {
            return results.stream()
                    .filter(result -> result.similarity() >= 0.60)
                    .toList();
        }

        return results;
    }

    private void appendOptionalFilters(
            StringBuilder sql,
            List<Object> args,
            String category,
            Double minPrice,
            Double maxPrice,
            List<String> productTerms,
            List<String> colourTerms,
            List<String> sizeTerms,
            boolean lexical) {

        if (category != null) {
            sql.append(" AND LOWER(c.name) = LOWER(?)");
            args.add(category);
        }
        if (minPrice != null) {
            sql.append(" AND p.price >= ?");
            args.add(minPrice);
        }
        if (maxPrice != null) {
            sql.append(" AND p.price <= ?");
            args.add(maxPrice);
        }

        // Match product type against name, description and category metadata.
        if (!productTerms.isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < productTerms.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("LOWER(CONCAT_WS(' ', p.name, c.name)) LIKE ?");
                args.add("%" + productTerms.get(i) + "%");
            }
            sql.append(")");
        }

        // Colour + size (or size alone) must match one active, in-stock variant.
        // Colour-only searches may also match an explicitly coloured parent product,
        // but only when that product has no variant rows at all.
        if (!colourTerms.isEmpty() || !sizeTerms.isEmpty()) {
            sql.append("""
                 AND (
                     EXISTS (
                         SELECT 1
                         FROM product_variants pv
                         WHERE pv.product_id = p.id
                           AND pv.active = TRUE
                           AND pv.stock > 0
                """);

            if (!colourTerms.isEmpty()) {
                sql.append(" AND (");
                for (int i = 0; i < colourTerms.size(); i++) {
                    if (i > 0) sql.append(" OR ");
                    sql.append("LOWER(TRIM(COALESCE(pv.color, ''))) = LOWER(?)");
                    args.add(colourTerms.get(i));
                }
                sql.append(")");
            }

            if (!sizeTerms.isEmpty()) {
                sql.append(" AND (");
                for (int i = 0; i < sizeTerms.size(); i++) {
                    if (i > 0) sql.append(" OR ");
                    sql.append("LOWER(TRIM(COALESCE(pv.size, ''))) = LOWER(?)");
                    args.add(sizeTerms.get(i));
                }
                sql.append(")");
            }

            sql.append(")");

            if (!colourTerms.isEmpty() && sizeTerms.isEmpty()) {
                sql.append("""
                     OR (
                         NOT EXISTS (
                             SELECT 1
                             FROM product_variants pv_any
                             WHERE pv_any.product_id = p.id
                         )
                         AND (
                """);
                for (int i = 0; i < colourTerms.size(); i++) {
                    if (i > 0) sql.append(" OR ");
                    sql.append("LOWER(TRIM(COALESCE(p.color, ''))) = LOWER(?)");
                    args.add(colourTerms.get(i));
                }
                sql.append("))");
            }

            sql.append(")");
        }
    }

    private List<String> findProductType(String normalized) {
        if (containsAny(normalized, "headphone", "earbud", "headset")) {
            return PRODUCT_TYPES.get("headphones");
        }
        if (containsAny(normalized, "backpack", "rucksack")) {
            return List.of("backpack", "rucksack");
        }
        if (containsAny(normalized, "jacket", "coat", "blazer", "windbreaker")) {
            return PRODUCT_TYPES.get("jackets");
        }
        if (containsAny(normalized, "shoe", "sneaker", "trainer", "boot", "sandal")) {
            return PRODUCT_TYPES.get("shoes");
        }
        if (containsAny(normalized, "shirt", "t shirt", "tee", "polo")) {
            return PRODUCT_TYPES.get("shirts");
        }
        if (containsAny(normalized, "hoodie", "sweatshirt")) {
            return PRODUCT_TYPES.get("hoodies");
        }
        if (containsAny(normalized, "jean", "trouser", "chino", "legging")) {
            return PRODUCT_TYPES.get("jeans");
        }
        if (containsAny(normalized, "speaker")) return PRODUCT_TYPES.get("speakers");
        if (containsAny(normalized, "watch")) return PRODUCT_TYPES.get("watches");
        if (containsAny(normalized, "wallet")) return PRODUCT_TYPES.get("wallets");
        if (containsAny(normalized, "sunglass")) return PRODUCT_TYPES.get("sunglasses");
        if (containsAny(normalized, "laptop", "notebook")) return PRODUCT_TYPES.get("laptops");
        return List.of();
    }

    private List<String> findColour(String normalized) {
        for (Map.Entry<String, List<String>> entry : COLOURS.entrySet()) {
            for (String alias : entry.getValue()) {
                if (containsAny(normalized, alias)) {
                    return entry.getValue();
                }
            }
        }
        return List.of();
    }


    private List<String> findSize(String normalized) {
        if (Pattern.compile("\\bone\\s+size\\b").matcher(normalized).find()) {
            return List.of("one size");
        }

        java.util.regex.Matcher numericMatcher =
                Pattern.compile("\\bsize\\s*(\\d{1,3})\\b").matcher(normalized);

        if (numericMatcher.find()) {
            return List.of(numericMatcher.group(1));
        }

        java.util.regex.Matcher matcher = Pattern.compile(
                "\\b(?:size\\s*)?(xxs|xs|s|m|l|xl|xxl|xxxl|2xl|3xl|4xl|small|medium|large)\\b"
        ).matcher(normalized);

        if (!matcher.find()) return List.of();

        String value = matcher.group(1);

        return switch (value) {
            case "small" -> List.of("s", "small");
            case "medium" -> List.of("m", "medium");
            case "large" -> List.of("l", "large");
            case "2xl" -> List.of("xxl", "2xl");
            case "3xl" -> List.of("xxxl", "3xl");
            default -> List.of(value);
        };
    }

    private boolean containsAny(String value, String... terms) {
        String[] words = value.split("\\s+");
        for (String term : terms) {
            for (String word : words) {
                if (word.equals(term)
                        || word.equals(term + "s")
                        || word.equals(term + "es")
                        || (term.endsWith("y")
                            && word.equals(term.substring(0, term.length() - 1) + "ies"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String toVectorString(List<Double> vector) {
        if (vector == null || vector.size() != embeddingClient.getDimensions()) {
            throw new IllegalArgumentException("Expected 384-dimensional vector.");
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            Double value = vector.get(i);
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Invalid embedding value.");
            }
            if (i > 0) builder.append(",");
            builder.append(value);
        }
        return builder.append("]").toString();
    }
}

