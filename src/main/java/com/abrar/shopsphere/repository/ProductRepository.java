
package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.dto.ProductResponseDTO;

import com.abrar.shopsphere.dto.ProductCatalogResponseDTO;
import com.abrar.shopsphere.entity.Product;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    // ============================================================
    // EXISTING QUERY METHODS
    // ============================================================

    boolean existsByName(String name);

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByPriceBetween(
            Double minPrice,
            Double maxPrice
    );

    List<Product> findByCategoryName(String categoryName);

// ============================================================
// SELLER DASHBOARD - FIND PRODUCTS BY SELLER
// ============================================================

List<Product> findBySeller_Id(Long sellerId);



    // ============================================================
    // UNIFIED PRODUCT CATALOG FILTERING
    // Optional search/category/price filters are applied in the database.
    // ============================================================

    @Query(
            value = """
                    SELECT new com.abrar.shopsphere.dto.ProductResponseDTO(
                        p.id, p.name, p.description, p.price, p.stock, p.imageUrl
                    )
                    FROM Product p
                    LEFT JOIN p.category c
                    WHERE (:search IS NULL OR :search = ''
                        OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))
                    AND (:category IS NULL OR :category = ''
                        OR LOWER(c.name) = LOWER(:category))
                    AND (:minPrice IS NULL OR p.price >= :minPrice)
                    AND (:maxPrice IS NULL OR p.price <= :maxPrice)
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Product p
                    LEFT JOIN p.category c
                    WHERE (:search IS NULL OR :search = ''
                        OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))
                    AND (:category IS NULL OR :category = ''
                        OR LOWER(c.name) = LOWER(:category))
                    AND (:minPrice IS NULL OR p.price >= :minPrice)
                    AND (:maxPrice IS NULL OR p.price <= :maxPrice)
                    """
    )
    Page<ProductResponseDTO> searchCatalog(
            @Param("search") String search,
            @Param("category") String category,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Pageable pageable
    );


    // ============================================================
    // PESSIMISTIC LOCKING
    // Used during order creation to protect inventory.
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


    // ============================================================
    // ENTITY GRAPH OPTIMIZATION
    // ============================================================

    @EntityGraph(attributePaths = {"category", "seller"})
    @Query("SELECT p FROM Product p")
    List<Product> findAllWithCategoryAndSeller();


    // ============================================================
    // JPQL JOIN FETCH OPTIMIZATION
    // ============================================================

    @Query("""
            SELECT p
            FROM Product p
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH p.seller
            """)
    List<Product> findAllWithJoinFetch();


    // ============================================================
    // DTO PROJECTION
    // Includes stock in product responses.
    // ============================================================

    @Query("""
            SELECT new com.abrar.shopsphere.dto.ProductResponseDTO(
                p.id,
                p.name,
                p.description,
                p.price,
                p.stock
            )
            FROM Product p
            """)
    Page<ProductResponseDTO> findAllProjected(
            Pageable pageable
    );


    // ============================================================
    // OPTIMIZED PRODUCT CATALOG
    // DTO PROJECTION + LEFT JOINS + PAGINATION
    // ============================================================

    @Query(
            value = """
                    SELECT new com.abrar.shopsphere.dto.ProductCatalogResponseDTO(
                        p.id,
                        p.name,
                        p.price,
                        p.stock,
                        c.name,
                        u.email
                    )
                    FROM Product p
                    LEFT JOIN p.category c
                    LEFT JOIN p.seller u
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Product p
                    """
    )
    Page<ProductCatalogResponseDTO> findCatalogPage(
            Pageable pageable
    );

}


