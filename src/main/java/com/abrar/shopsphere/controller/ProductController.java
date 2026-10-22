package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.ProductRequestDTO;
import com.abrar.shopsphere.dto.ProductResponseDTO;
import com.abrar.shopsphere.service.ProductService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ============================================================
    // CREATE PRODUCT
    // ============================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponseDTO addProduct(
            @Valid
            @RequestBody ProductRequestDTO requestDTO) {

        return productService.addProduct(requestDTO);
    }

    // ============================================================
    // GET ALL PRODUCTS
    // ============================================================

    @GetMapping
    public Page<ProductResponseDTO> getAllProducts(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(defaultValue = "id")
            String sortBy) {

        return productService.getAllProducts(
                page,
                size,
                sortBy
        );
    }

    // ============================================================
    // GET PRODUCT BY ID
    // ============================================================

    @GetMapping("/{id}")
    public ProductResponseDTO getProductById(
            @PathVariable Long id) {

        return productService.getProductById(id);
    }

    // ============================================================
    // UPDATE PRODUCT
    // ============================================================

    @PutMapping("/{id}")
    public ProductResponseDTO updateProduct(

            @PathVariable Long id,

            @Valid
            @RequestBody ProductRequestDTO requestDTO) {

        return productService.updateProduct(
                id,
                requestDTO
        );
    }

    // ============================================================
    // DELETE PRODUCT
    // ============================================================

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @PathVariable Long id) {

        productService.deleteProduct(id);
    }

    // ============================================================
    // SEARCH PRODUCTS
    // ============================================================

    @GetMapping("/search")
    public List<ProductResponseDTO> searchProducts(

            @RequestParam String name) {

        return productService.searchProducts(name);
    }

    // ============================================================
    // FILTER PRODUCTS BY PRICE
    // ============================================================

    @GetMapping("/price")
    public List<ProductResponseDTO> getProductsByPriceRange(

            @RequestParam Double minPrice,

            @RequestParam Double maxPrice) {

        return productService.getProductsByPriceRange(
                minPrice,
                maxPrice
        );
    }

    // ============================================================
    // FILTER PRODUCTS BY CATEGORY
    // ============================================================

    @GetMapping("/category")
    public List<ProductResponseDTO> getProductsByCategory(

            @RequestParam String categoryName) {

        return productService.getProductsByCategory(
                categoryName
        );
    }
}