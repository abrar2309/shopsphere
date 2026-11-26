
package com.abrar.shopsphere.dto;

import java.util.List;

public class CategoryWithProductsResponseDTO {

    private Long id;
    private String name;
    private List<CategoryProductResponseDTO> products;

    public CategoryWithProductsResponseDTO() {
    }

    public CategoryWithProductsResponseDTO(
            Long id,
            String name,
            List<CategoryProductResponseDTO> products) {

        this.id = id;
        this.name = name;
        this.products = products;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<CategoryProductResponseDTO> getProducts() {
        return products;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setProducts(
            List<CategoryProductResponseDTO> products) {

        this.products = products;
    }
}
