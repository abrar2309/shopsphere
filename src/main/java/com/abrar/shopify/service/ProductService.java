package com.abrar.shopify.service;

import com.abrar.shopify.entity.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private final List<Product> products = new ArrayList<>();

    public ProductService() {

        products.add(new Product(
                1L,
                "iPhone 16 Pro",
                "Apple flagship smartphone",
                new BigDecimal("129999"),
                10
        ));

        products.add(new Product(
                2L,
                "MacBook Pro M4",
                "Apple Laptop",
                new BigDecimal("189999"),
                5
        ));

        products.add(new Product(
                3L,
                "Samsung Smart TV",
                "55 inch 4K Smart TV",
                new BigDecimal("69999"),
                15
        ));
    }

    public List<Product> getAllProducts() {
        return products;
    }
    public Product addProduct(Product product) {

        products.add(product);

        return product;

    }
    public Product getProductById(Long id) {

        for (Product product : products) {

            if (product.getId().equals(id)) {
                return product;
            }

        }

        return null;
    }
    public Product updateProduct(Long id, Product updatedProduct) {

        for (Product product : products) {

            if (product.getId().equals(id)) {

                product.setName(updatedProduct.getName());
                product.setDescription(updatedProduct.getDescription());
                product.setPrice(updatedProduct.getPrice());
                product.setStock(updatedProduct.getStock());

                return product;
            }

        }

        return null;
    }
    public Product deleteProduct(Long id) {

        for (Product product : products) {

            if (product.getId().equals(id)) {

                products.remove(product);

                return product;

            }

        }

        return null;
    }

}