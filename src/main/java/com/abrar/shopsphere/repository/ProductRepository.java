package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.entity.Product;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    // ============================================================
    // EXISTING PRODUCT METHODS
    // ============================================================

    boolean existsByName(String name);

    List<Product> findByNameContainingIgnoreCase(
            String name
    );

    List<Product> findByPriceBetween(
            Double minPrice,
            Double maxPrice
    );

    List<Product> findByCategoryName(
            String categoryName
    );


    // ============================================================
    // MONTH 7 — CONCURRENCY
    // PESSIMISTIC WRITE LOCK
    // ============================================================

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM Product p
            WHERE p.id = :productId
            """)
    Optional<Product> findByIdForUpdate(
            @Param("productId") Long productId
    );
}