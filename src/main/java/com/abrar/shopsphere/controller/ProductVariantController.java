package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.ProductVariantRequestDTO;
import com.abrar.shopsphere.dto.ProductVariantResponseDTO;
import com.abrar.shopsphere.service.ProductVariantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/products/{productId}/variants")
public class ProductVariantController {
    private final ProductVariantService service;
    public ProductVariantController(ProductVariantService service) { this.service = service; }

    @GetMapping
    public List<ProductVariantResponseDTO> list(@PathVariable Long productId) {
        return service.listPublic(productId);
    }

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER')")
    public List<ProductVariantResponseDTO> listSeller(@PathVariable Long productId) {
        return service.listSeller(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SELLER')")
    public ProductVariantResponseDTO create(@PathVariable Long productId,
            @Valid @RequestBody ProductVariantRequestDTO request) {
        return service.create(productId, request);
    }

    @PutMapping("/{variantId}")
    @PreAuthorize("hasRole('SELLER')")
    public ProductVariantResponseDTO update(@PathVariable Long productId, @PathVariable Long variantId,
            @Valid @RequestBody ProductVariantRequestDTO request) {
        return service.update(productId, variantId, request);
    }

    @DeleteMapping("/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SELLER')")
    public void deactivate(@PathVariable Long productId, @PathVariable Long variantId) {
        service.deactivate(productId, variantId);
    }
}
