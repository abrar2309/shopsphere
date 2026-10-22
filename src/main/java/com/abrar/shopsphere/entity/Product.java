package com.abrar.shopsphere.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(
        name = "products",
        indexes = {
                @Index(
                        name = "idx_product_name",
                        columnList = "name"
                ),
                @Index(
                        name = "idx_product_category",
                        columnList = "category_id"
                ),
                @Index(
                        name = "idx_product_price",
                        columnList = "price"
                ),
                @Index(
                        name = "idx_product_seller",
                        columnList = "seller_id"
                )
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotBlank(
            message = "Product name cannot be blank"
    )
    @Size(
            min = 3,
            max = 100,
            message = "Product name must be between 3 and 100 characters"
    )
    private String name;


    @NotBlank(
            message = "Product description cannot be blank"
    )
    @Size(
            min = 10,
            max = 500,
            message = "Description must be between 10 and 500 characters"
    )
    private String description;


    @Positive(
            message = "Price must be greater than 0"
    )
    private Double price;


    // ============================================================
    // INVENTORY
    // ============================================================

    @PositiveOrZero(message = "Stock cannot be negative")
    private Integer stock;

    // ============================================================
    // OPTIMISTIC LOCKING
    // ============================================================

    @Version
    private Long version;


    // ============================================================
    // CATEGORY
    // ============================================================

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;


    // ============================================================
    // SELLER
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;


    // ============================================================
    // DEFAULT CONSTRUCTOR
    // ============================================================

    public Product() {
    }


    // ============================================================
    // PARAMETERIZED CONSTRUCTOR
    // ============================================================

    public Product(
            Long id,
            String name,
            String description,
            Double price,
            Integer stock,
            Category category) {

        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }


    // ============================================================
    // GETTERS AND SETTERS
    // ============================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }


    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }


    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }


    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }


    // ============================================================
    // SELLER GETTER / SETTER
    // ============================================================

    public User getSeller() {
        return seller;
    }

    public void setSeller(User seller) {
        this.seller = seller;
    }
}