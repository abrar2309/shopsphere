
        package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.entity.Order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository
        extends JpaRepository<Order, Long> {

    @Query("""
        SELECT DISTINCT o
        FROM Order o
        LEFT JOIN FETCH o.items i
        LEFT JOIN FETCH i.product
        WHERE o.user.id = :userId
        ORDER BY o.id DESC
        """)
    List<Order> findOrdersWithItemsByUserId(
            @Param("userId") Long userId
    );
}
