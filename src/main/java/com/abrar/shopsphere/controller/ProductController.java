package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import java.util.List;

@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public List<Product> getAllProducts() {

        return productService.getAllProducts();

    }
    @PostMapping("/products")
    public Product addProduct(@RequestBody Product product) {

        return productService.addProduct(product);

    }
    @GetMapping("/products/{id}")
    public Product getProductById(@PathVariable Long id) {

        return productService.getProductById(id);

    }
    @PutMapping("/products/{id}")
    public Product updateProduct(
            @PathVariable Long id,
            @RequestBody Product product) {

        return productService.updateProduct(id, product);

    }
    @DeleteMapping("/products/{id}")
    public Product deleteProduct(@PathVariable Long id) {

        return productService.deleteProduct(id);

    }
}