package com.abrar.shopsphere.dto;

public class ProductResponseDTO {

    private Long id;

    private String name;

    private String description;

    private Double price;

    // No-args constructor
    public ProductResponseDTO() {
    }

    // All-args constructor
    public ProductResponseDTO(
            Long id,
            String name,
            String description,
            Double price) {

        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    // Getters

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Double getPrice() {
        return price;
    }

    // Setters

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}