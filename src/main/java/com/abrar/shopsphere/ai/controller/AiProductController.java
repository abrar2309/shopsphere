
package com.abrar.shopsphere.ai.controller;

import com.abrar.shopsphere.ai.dto.AiProductSearchResponse;
import com.abrar.shopsphere.ai.dto.EmbeddingIndexResponse;
import com.abrar.shopsphere.ai.service.ProductEmbeddingService;
import com.abrar.shopsphere.ai.service.SemanticProductSearchService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class AiProductController {

    private final ProductEmbeddingService embeddingService;
    private final SemanticProductSearchService searchService;

    public AiProductController(
            ProductEmbeddingService embeddingService,
            SemanticProductSearchService searchService) {

        this.embeddingService = embeddingService;
        this.searchService = searchService;
    }

    // Admin-only because SecurityConfig protects /admin/**.
    @PostMapping("/admin/ai/products/index")
    @ResponseStatus(HttpStatus.OK)
    public EmbeddingIndexResponse indexProducts() {

        return embeddingService.indexAllProducts();
    }

    // Public read-only semantic product search.
    @GetMapping("/ai/products/search")
    public List<AiProductSearchResponse> searchProducts(

            @RequestParam String query,

            @RequestParam(defaultValue = "10")
            int limit) {

        return searchService.search(query, limit);
    }
}
