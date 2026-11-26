
package com.abrar.shopsphere.dto;

public class ProductCatalogResponseDTO {

    private Long id;
    private String name;
    private Double price;
    private Integer stock;
    private String categoryName;
    private String sellerEmail;

    public ProductCatalogResponseDTO(
            Long id,
            String name,
            Double price,
            Integer stock,
            String categoryName,
            String sellerEmail) {

        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.categoryName = categoryName;
        this.sellerEmail = sellerEmail;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getSellerEmail() {
        return sellerEmail;
    }
}
