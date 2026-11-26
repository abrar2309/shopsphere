package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.ProductResponseDTO;

import com.abrar.shopsphere.dto.ProductCatalogResponseDTO;
import com.abrar.shopsphere.dto.ProductRequestDTO;
import com.abrar.shopsphere.service.ProductService;
import com.abrar.shopsphere.service.ProductImageService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;

    public ProductController(ProductService productService, ProductImageService productImageService) {
        this.productService = productService;
        this.productImageService = productImageService;
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


    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponseDTO uploadProductImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return productImageService.upload(id, file);
    }

    // ============================================================

    // ============================================================
    // SELLER DASHBOARD - GET MY PRODUCTS
    // ============================================================

    @GetMapping("/mine")
    public List<ProductResponseDTO> getMyProducts() {

        return productService.getMyProducts();
    }
    // GET ALL PRODUCTS
    // ============================================================

    @GetMapping
    public Page<ProductResponseDTO> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice) {

        return productService.getAllProducts(
                page, size, sortBy, direction,
                search, category, minPrice, maxPrice
        );
    }


    // ============================================================
    // MONTH 10 - OPTIMIZED PRODUCT CATALOG
    // ============================================================

    @GetMapping("/catalog")
    public Page<ProductCatalogResponseDTO> getProductCatalog(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(defaultValue = "price")
            String sortBy) {

        return productService.getProductCatalog(
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

