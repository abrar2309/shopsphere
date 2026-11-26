
package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Find category by exact name
    Optional<Category> findByName(String name);

    // Check if category already exists
    boolean existsByName(String name);

    // Query 1: Fetch only category IDs using database pagination
    @Query("SELECT c.id FROM Category c ORDER BY c.id")
    Page<Long> findCategoryIds(Pageable pageable);

    // Query 2: Fetch selected categories with their products
    @Query("""
            SELECT DISTINCT c
            FROM Category c
            LEFT JOIN FETCH c.products
            WHERE c.id IN :ids
            """)
    List<Category> findAllWithProductsByIdIn(
            @Param("ids") Collection<Long> ids
    );
}
