package com.abrar.shopsphere.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class BgeEmbeddingClient {

    private static final int DIMENSIONS = 384;
    private static final int MAX_BATCH_SIZE = 32;
    private static final int MAX_INPUT_LENGTH = 8_000;
    private static final int MAX_ERROR_BODY_LENGTH = 1_000;

    private final ObjectMapper objectMapper;
    private final URI embeddingUri;
    private final String expectedModel;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public BgeEmbeddingClient(
            ObjectMapper objectMapper,
            @Value("${shopsphere.ai.bge.base-url:http://localhost:8001}")
            String baseUrl,
            @Value("${shopsphere.ai.bge.model:BAAI/bge-small-en-v1.5}")
            String expectedModel,
            @Value("${shopsphere.ai.bge.connect-timeout:10s}")
            java.time.Duration connectTimeout,
            @Value("${shopsphere.ai.bge.read-timeout:120s}")
            java.time.Duration readTimeout) {

        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.embeddingUri = buildEmbeddingUri(baseUrl);
        this.expectedModel = requireText(expectedModel, "BGE model name");
        this.connectTimeoutMillis = toTimeoutMillis(connectTimeout, "connect");
        this.readTimeoutMillis = toTimeoutMillis(readTimeout, "read");
    }

    public List<List<Double>> generateDocumentEmbeddings(List<String> inputs) {
        return generate(inputs, "document");
    }

    public List<Double> generateQueryEmbedding(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Embedding query cannot be empty.");
        }

        List<List<Double>> result = generate(List.of(query.trim()), "query");
        return result.get(0);
    }

    private List<List<Double>> generate(List<String> inputs, String inputType) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }

        if (inputs.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("BGE batch cannot exceed 32 inputs.");
        }

        List<String> cleanedInputs = inputs.stream()
                .map(value -> {
                    if (value == null || value.isBlank()) {
                        throw new IllegalArgumentException(
                                "BGE inputs cannot contain blank text.");
                    }
                    String cleaned = value.trim();
                    if (cleaned.length() > MAX_INPUT_LENGTH) {
                        throw new IllegalArgumentException(
                                "BGE input cannot exceed 8000 characters.");
                    }
                    return cleaned;
                })
                .toList();

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(
                    Map.of("input", cleanedInputs, "input_type", inputType));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize the BGE request.", exception);
        }

        byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        String responseBody;
        int status;

        try {
            connection = (HttpURLConnection) embeddingUri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setDoOutput(true);
            connection.setRequestProperty(
                    "Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setFixedLengthStreamingMode(requestBytes.length);

            try (var output = connection.getOutputStream()) {
                output.write(requestBytes);
                output.flush();
            }

            status = connection.getResponseCode();
            var responseStream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (responseStream == null) {
                throw new IllegalStateException(
                        "BGE service returned HTTP " + status
                                + " without a response body.");
            }

            try (responseStream) {
                responseBody = new String(
                        responseStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (status < 200 || status >= 300) {
                throw new IllegalStateException(
                        "BGE service returned HTTP " + status + ": "
                                + abbreviate(responseBody));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not communicate with the BGE embedding service. "
                            + "Check its availability and configured URL.",
                    exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        EmbeddingResponse response;
        try {
            response = objectMapper.readValue(
                    responseBody, EmbeddingResponse.class);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not parse the BGE embedding response.", exception);
        }

        validateResponse(response, cleanedInputs.size());

        List<List<Double>> ordered = new ArrayList<>(
                Collections.nCopies(cleanedInputs.size(), null));
        Set<Integer> seen = new HashSet<>();

        for (EmbeddingItem item : response.data()) {
            if (item == null
                    || item.index() < 0
                    || item.index() >= cleanedInputs.size()
                    || !seen.add(item.index())) {
                throw new IllegalStateException(
                        "BGE returned an invalid or duplicate embedding index.");
            }

            List<Double> vector = item.embedding();
            if (vector == null || vector.size() != DIMENSIONS) {
                throw new IllegalStateException(
                        "BGE returned an invalid vector dimension.");
            }

            for (Double value : vector) {
                if (value == null || !Double.isFinite(value)) {
                    throw new IllegalStateException(
                            "BGE returned a non-finite embedding value.");
                }
            }

            ordered.set(item.index(), List.copyOf(vector));
        }

        if (ordered.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException(
                    "BGE response did not contain every requested embedding.");
        }

        return List.copyOf(ordered);
    }

    private void validateResponse(EmbeddingResponse response, int expectedCount) {
        if (response == null) {
            throw new IllegalStateException(
                    "BGE service returned an empty response.");
        }
        if (!expectedModel.equals(response.model())) {
            throw new IllegalStateException(
                    "Unexpected BGE model returned by the embedding service.");
        }
        if (response.dimensions() != DIMENSIONS) {
            throw new IllegalStateException(
                    "BGE must return exactly 384 embedding dimensions.");
        }
        if (response.data() == null || response.data().size() != expectedCount) {
            throw new IllegalStateException(
                    "BGE embedding count did not match the request.");
        }
    }

    private URI buildEmbeddingUri(String baseUrl) {
        String cleaned = requireText(baseUrl, "BGE base URL")
                .replaceAll("/+$", "");
        URI base;
        try {
            base = URI.create(cleaned);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "BGE base URL is not a valid URI.", exception);
        }

        if (!("http".equalsIgnoreCase(base.getScheme())
                || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null) {
            throw new IllegalArgumentException(
                    "BGE base URL must be an HTTP(S) URL without credentials, "
                            + "query parameters, or fragments.");
        }

        return URI.create(cleaned + "/v1/embeddings");
    }

    private int toTimeoutMillis(java.time.Duration timeout, String name) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException(
                    "BGE " + name + " timeout must be greater than zero.");
        }
        long millis = timeout.toMillis();
        if (millis < 1 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "BGE " + name + " timeout is outside the supported range.");
        }
        return (int) millis;
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        return value.trim();
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return cleaned.length() <= MAX_ERROR_BODY_LENGTH
                ? cleaned
                : cleaned.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }

    public String getModelName() {
        return expectedModel;
    }

    public int getDimensions() {
        return DIMENSIONS;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(
            String model,
            int dimensions,
            List<EmbeddingItem> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingItem(
            int index,
            List<Double> embedding) {
    }
}
