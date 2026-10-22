package com.abrar.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CategoryRequestDTO {

    @NotBlank(message = "Category name cannot be empty")
    @Size(min = 3,
            max = 50,
            message = "Category name must be between 3 and 50 characters")
    private String name;

    public CategoryRequestDTO() {
    }

    public CategoryRequestDTO(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}