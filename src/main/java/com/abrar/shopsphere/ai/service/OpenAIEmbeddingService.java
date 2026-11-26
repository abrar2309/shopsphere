
package com.abrar.shopsphere.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIEmbeddingService {

    private static final int DIMENSIONS = 1536;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    public OpenAIEmbeddingService(
            RestClient restClient,
            ObjectMapper objectMapper) {

        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<List<Double>> generateEmbeddings(
            List<String> inputs) {

        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is not configured."
            );
        }

        Map<String, Object> request = Map.of(
                "model", embeddingModel,
                "input", inputs,
                "dimensions", DIMENSIONS,
                "encoding_format", "float"
        );

        try {

            String response = restClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            if (!data.isArray() || data.size() != inputs.size()) {
                throw new IllegalStateException(
                        "Unexpected embedding count from OpenAI."
                );
            }

            List<JsonNode> orderedData = new ArrayList<>();

            data.forEach(orderedData::add);

            orderedData.sort(
                    Comparator.comparingInt(
                            node -> node.path("index").asInt(-1)
                    )
            );

            List<List<Double>> results = new ArrayList<>();

            for (int i = 0; i < orderedData.size(); i++) {

                JsonNode embeddingNode =
                        orderedData.get(i).path("embedding");

                if (!embeddingNode.isArray()
                        || embeddingNode.size() != DIMENSIONS) {

                    throw new IllegalStateException(
                            "Invalid embedding dimensions."
                    );
                }

                List<Double> vector =
                        new ArrayList<>(DIMENSIONS);

                for (JsonNode value : embeddingNode) {

                    double number = value.asDouble();

                    if (!Double.isFinite(number)) {
                        throw new IllegalStateException(
                                "Invalid embedding value."
                        );
                    }

                    vector.add(number);
                }

                results.add(vector);
            }

            return results;

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to generate OpenAI embeddings.",
                    exception
            );
        }
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }
}
