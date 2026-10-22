package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Find category by exact name
    Optional<Category> findByName(String name);

    // Check if category already exists
    boolean existsByName(String name);

}