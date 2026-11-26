package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.WishlistItemDTO;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistService {

    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public WishlistService(
            JdbcTemplate jdbc,
            UserRepository users
    ) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<WishlistItemDTO> getWishlist(String email) {
        User user = requireUser(email);

        return jdbc.query("""
                SELECT
                    wi.id,
                    p.id AS product_id,
                    p.name,
                    p.description,
                    p.price,
                    p.stock,
                    p.image_url,
                    c.name AS category,
                    wi.created_at
                FROM wishlist_items wi
                JOIN products p ON p.id = wi.product_id
                LEFT JOIN categories c ON c.id = p.category_id
                WHERE wi.user_id = ?
                ORDER BY wi.created_at DESC, wi.id DESC
                """,
                (rs, rowNum) -> new WishlistItemDTO(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getObject("price", Double.class),
                        rs.getObject("stock", Integer.class),
                        rs.getString("image_url"),
                        rs.getString("category"),
                        rs.getObject(
                                "created_at",
                                java.time.OffsetDateTime.class
                        )
                ),
                user.getId()
        );
    }

    @Transactional
    public void add(String email, Long productId) {
        User user = requireUser(email);

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Invalid product ID.");
        }

        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM products
                WHERE id = ?
                """,
                Integer.class,
                productId
        );

        if (exists == null || exists == 0) {
            throw new IllegalArgumentException("Product not found.");
        }

        jdbc.update("""
                INSERT INTO wishlist_items(user_id, product_id)
                VALUES (?, ?)
                ON CONFLICT (user_id, product_id) DO NOTHING
                """,
                user.getId(),
                productId
        );
    }

    @Transactional
    public void remove(String email, Long productId) {
        User user = requireUser(email);

        jdbc.update("""
                DELETE FROM wishlist_items
                WHERE user_id = ?
                  AND product_id = ?
                """,
                user.getId(),
                productId
        );
    }

    @Transactional(readOnly = true)
    public boolean contains(String email, Long productId) {
        User user = requireUser(email);

        Boolean result = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1
                    FROM wishlist_items
                    WHERE user_id = ?
                      AND product_id = ?
                )
                """,
                Boolean.class,
                user.getId(),
                productId
        );

        return Boolean.TRUE.equals(result);
    }

    private User requireUser(String email) {
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Authentication required.");
        }

        return users.findByEmail(email)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "Authenticated user not found."
                        )
                );
    }
}