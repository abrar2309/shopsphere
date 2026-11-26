package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.ProductVariant;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.ProductVariantRepository;
import com.abrar.shopsphere.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@Service
public class ProductVariantImageService {
    private final JdbcTemplate jdbc;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final UserRepository users;
    private final ProductImageStorageService storage;

    public ProductVariantImageService(JdbcTemplate jdbc, ProductVariantRepository variants,
            ProductRepository products, UserRepository users, ProductImageStorageService storage) {
        this.jdbc = jdbc; this.variants = variants; this.products = products;
        this.users = users; this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> list(Long productId, Long variantId) {
        requireVariant(productId, variantId, false);
        return jdbc.queryForList("""
            SELECT id, variant_id AS "variantId", image_url AS "imageUrl",
                   display_order AS "displayOrder", is_primary AS "primary",
                   created_at AS "createdAt"
            FROM product_variant_images
            WHERE variant_id = ?
            ORDER BY display_order, id
            """, variantId);
    }

    @Transactional
    public List<Map<String,Object>> upload(Long productId, Long variantId, List<MultipartFile> files) {
        requireVariant(productId, variantId, true);
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("Select at least one image.");
        if (files.size() > 10) throw new IllegalArgumentException("Upload at most 10 images at a time.");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM product_variant_images WHERE variant_id=?", Integer.class, variantId);
        int existing = count == null ? 0 : count;
        if (existing + files.size() > 10) throw new IllegalArgumentException("A variant can have at most 10 images.");
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        try {
            for (MultipartFile file : files) urls.add(storage.store(file));
            Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(display_order),-1) FROM product_variant_images WHERE variant_id=?", Integer.class, variantId);
            int order = max == null ? 0 : max + 1;
            Boolean hasPrimary = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM product_variant_images WHERE variant_id=? AND is_primary=TRUE)", Boolean.class, variantId);
            for (int i=0; i<urls.size(); i++) {
                jdbc.update("INSERT INTO product_variant_images(variant_id,image_url,display_order,is_primary) VALUES(?,?,?,?)",
                    variantId, urls.get(i), order+i, !Boolean.TRUE.equals(hasPrimary) && i==0);
            }
            return list(productId, variantId);
        } catch (RuntimeException ex) {
            urls.forEach(storage::deleteByPublicUrl);
            throw ex;
        }
    }

    @Transactional
    public void delete(Long productId, Long variantId, Long imageId) {
        requireVariant(productId, variantId, true);
        Map<String,Object> image = jdbc.query("""
            SELECT id, image_url AS "imageUrl", is_primary AS primary
            FROM product_variant_images WHERE id=? AND variant_id=?
            """, rs -> rs.next() ? Map.of("id",rs.getLong("id"),"imageUrl",rs.getString("image_url"),"primary",rs.getBoolean("is_primary")) : null,
            imageId, variantId);
        if (image == null) throw new IllegalArgumentException("Variant image not found.");
        jdbc.update("DELETE FROM product_variant_images WHERE id=? AND variant_id=?", imageId, variantId);
        if (Boolean.TRUE.equals(image.get("primary"))) {
            jdbc.update("UPDATE product_variant_images SET is_primary=FALSE WHERE variant_id=?", variantId);
            jdbc.update("""
                UPDATE product_variant_images SET is_primary=TRUE
                WHERE id=(SELECT id FROM product_variant_images WHERE variant_id=? ORDER BY display_order,id LIMIT 1)
                """, variantId);
        }
        storage.deleteByPublicUrl(String.valueOf(image.get("imageUrl")));
    }

    private ProductVariant requireVariant(Long productId, Long variantId, boolean ownerRequired) {
        ProductVariant variant = variants.findById(variantId)
            .filter(v -> v.getProduct().getId().equals(productId))
            .orElseThrow(() -> new IllegalArgumentException("Variant not found."));
        if (ownerRequired) {
            Product product = products.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
                throw new AccessDeniedException("Authentication required.");
            boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            User user = users.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found."));
            if (!admin && (product.getSeller() == null || !product.getSeller().getId().equals(user.getId())))
                throw new AccessDeniedException("You can only manage images for your own products.");
        }
        return variant;
    }
}