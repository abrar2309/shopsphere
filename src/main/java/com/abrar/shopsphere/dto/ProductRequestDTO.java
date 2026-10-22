package com.abrar.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ProductRequestDTO {

    @NotBlank(message = "Product name cannot be blank")
    @Size(
            min = 3,
            max = 100,
            message = "Product name must be between 3 and 100 characters"
    )
    private String name;


    @NotBlank(message = "Product description cannot be blank")
    @Size(
            min = 10,
            max = 500,
            message = "Description must be between 10 and 500 characters"
    )
    private String description;


    @Positive(message = "Price must be greater than 0")
    private Double price;


    @Positive(message = "Stock must be greater than 0")
    private Integer stock;


    // ============================================================
    // GETTERS
    // ============================================================

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Double getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }


    // ============================================================
    // SETTERS
    // ============================================================

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}