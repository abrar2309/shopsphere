
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.ProductImageDTO;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductGalleryService {

    private final JdbcTemplate jdbc;
    private final ProductRepository products;
    private final UserRepository users;
    private final ProductImageStorageService storage;

    public ProductGalleryService(
            JdbcTemplate jdbc,
            ProductRepository products,
            UserRepository users,
            ProductImageStorageService storage) {

        this.jdbc = jdbc;
        this.products = products;
        this.users = users;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<ProductImageDTO> getImages(Long productId) {

        requireProduct(productId);

        return jdbc.query("""
                SELECT id, product_id, image_url,
                       display_order, is_primary, created_at
                FROM product_images
                WHERE product_id = ?
                ORDER BY display_order ASC, id ASC
                """, this::mapImage, productId);
    }

    @Transactional
    public List<ProductImageDTO> upload(
            Long productId,
            List<MultipartFile> files) {

        Product product = requireOwnedProduct(productId);

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException(
                    "Select at least one image."
            );
        }

        if (files.size() > 10) {
            throw new IllegalArgumentException(
                    "You can upload at most 10 images at a time."
            );
        }

        Integer currentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_images WHERE product_id = ?",
                Integer.class,
                productId
        );

        int existing = currentCount == null ? 0 : currentCount;

        if (existing + files.size() > 10) {
            throw new IllegalArgumentException(
                    "A product can have at most 10 images."
            );
        }

        List<String> storedUrls = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                storedUrls.add(storage.store(file));
            }

            Integer maxOrder = jdbc.queryForObject("""
                    SELECT COALESCE(MAX(display_order), -1)
                    FROM product_images
                    WHERE product_id = ?
                    """, Integer.class, productId);

            int nextOrder = maxOrder == null ? 0 : maxOrder + 1;

            boolean hasPrimary = Boolean.TRUE.equals(
                    jdbc.queryForObject("""
                            SELECT EXISTS(
                                SELECT 1 FROM product_images
                                WHERE product_id = ? AND is_primary = TRUE
                            )
                            """, Boolean.class, productId)
            );

            for (int i = 0; i < storedUrls.size(); i++) {
                boolean primary = !hasPrimary && i == 0;

                jdbc.update("""
                        INSERT INTO product_images
                            (product_id, image_url, display_order, is_primary)
                        VALUES (?, ?, ?, ?)
                        """,
                        productId,
                        storedUrls.get(i),
                        nextOrder + i,
                        primary
                );

                if (primary) {
                    product.setImageUrl(storedUrls.get(i));
                }
            }

            products.save(product);

            return getImages(productId);

        } catch (RuntimeException exception) {
            storedUrls.forEach(storage::deleteByPublicUrl);
            throw exception;
        }
    }

    @Transactional
    public void setPrimary(Long productId, Long imageId) {

        Product product = requireOwnedProduct(productId);

        String imageUrl = jdbc.query("""
                SELECT image_url
                FROM product_images
                WHERE id = ? AND product_id = ?
                """,
                result -> result.next() ? result.getString(1) : null,
                imageId,
                productId
        );

        if (imageUrl == null) {
            throw new IllegalArgumentException(
                    "Image not found for this product."
            );
        }

        // Clear the old primary first to satisfy the partial unique index.
        jdbc.update("""
                UPDATE product_images
                SET is_primary = FALSE
                WHERE product_id = ?
                """, productId);

        jdbc.update("""
                UPDATE product_images
                SET is_primary = TRUE
                WHERE id = ? AND product_id = ?
                """, imageId, productId);

        product.setImageUrl(imageUrl);
        products.save(product);
    }

    @Transactional
    public void delete(Long productId, Long imageId) {

        Product product = requireOwnedProduct(productId);

        ProductImageDTO image = jdbc.query("""
                SELECT id, product_id, image_url,
                       display_order, is_primary, created_at
                FROM product_images
                WHERE id = ? AND product_id = ?
                """,
                result -> result.next() ? mapImage(result, 0) : null,                imageId,
                productId
        );

        if (image == null) {
            throw new IllegalArgumentException(
                    "Image not found for this product."
            );
        }

        jdbc.update("""
                DELETE FROM product_images
                WHERE id = ? AND product_id = ?
                """, imageId, productId);

        List<ProductImageDTO> remaining = getImages(productId);

        if (Boolean.TRUE.equals(image.primary())) {
            jdbc.update("""
                    UPDATE product_images
                    SET is_primary = FALSE
                    WHERE product_id = ?
                    """, productId);

            if (!remaining.isEmpty()) {
                ProductImageDTO nextPrimary = remaining.get(0);

                jdbc.update("""
                        UPDATE product_images
                        SET is_primary = TRUE
                        WHERE id = ?
                        """, nextPrimary.id());

                product.setImageUrl(nextPrimary.imageUrl());
            } else {
                product.setImageUrl(null);
            }

            products.save(product);
        }

        storage.deleteByPublicUrl(image.imageUrl());
    }

    private Product requireProduct(Long productId) {
        return products.findById(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found."));
    }

    private Product requireOwnedProduct(Long productId) {

        Product product = requireProduct(productId);

        User current = currentUser();

        boolean admin = isAdmin();

        if (!admin && (product.getSeller() == null
                || !product.getSeller().getId().equals(current.getId()))) {
            throw new AccessDeniedException(
                    "You can only manage images for your own products."
            );
        }

        return product;
    }

    private User currentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication required.");
        }

        return users.findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "Authenticated user not found."
                        ));
    }

    private boolean isAdmin() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private ProductImageDTO mapImage(ResultSet rs, int row)
            throws SQLException {

        return new ProductImageDTO(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("image_url"),
                rs.getInt("display_order"),
                rs.getBoolean("is_primary"),
                rs.getObject("created_at",
                        java.time.OffsetDateTime.class)
        );
    }
}
