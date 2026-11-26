package com.abrar.shopsphere.ai.controller;

import com.abrar.shopsphere.ai.dto.AiProductSearchResponse;
import com.abrar.shopsphere.ai.service.BgeProductEmbeddingService;
import com.abrar.shopsphere.ai.service.BgeSemanticProductSearchService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BgeAiProductController {

    private final BgeProductEmbeddingService embeddingService;
    private final BgeSemanticProductSearchService searchService;

    public BgeAiProductController(
            BgeProductEmbeddingService embeddingService,
            BgeSemanticProductSearchService searchService) {
        this.embeddingService = embeddingService;
        this.searchService = searchService;
    }

    @PostMapping("/admin/ai/bge/products/index")
    @ResponseStatus(HttpStatus.OK)
    public BgeProductEmbeddingService.EmbeddingIndexResult indexProducts() {
        return embeddingService.indexAllProducts();
    }

    @GetMapping("/ai/bge/products/search")
    public List<AiProductSearchResponse> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice) {

        return searchService.search(
                query, limit, category, minPrice, maxPrice);
    }
}