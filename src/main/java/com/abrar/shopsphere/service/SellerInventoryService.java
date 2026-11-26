package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.InventoryAdjustmentRequest;
import com.abrar.shopsphere.dto.InventoryMovementDTO;
import com.abrar.shopsphere.dto.LowStockThresholdRequest;
import com.abrar.shopsphere.dto.SellerInventorySummaryDTO;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;
import com.abrar.shopsphere.dto.InventoryAlertDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class SellerInventoryService {

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public SellerInventoryService(
            JdbcTemplate jdbcTemplate,
            ProductRepository productRepository,
            UserRepository userRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------
    // INVENTORY SUMMARY
    // ------------------------------------------------------------
    @Transactional(readOnly = true)
    public SellerInventorySummaryDTO getSummary() {

        User seller = getCurrentSeller();

        List<SellerInventorySummaryDTO> results = jdbcTemplate.query(
                """
                SELECT
                    seller_id,
                    product_count,
                    total_units,
                    inventory_value,
                    out_of_stock_products,
                    low_stock_products
                FROM seller_inventory_summary
                WHERE seller_id = ?
                """,
                (rs, rowNum) -> new SellerInventorySummaryDTO(
                        rs.getLong("seller_id"),
                        rs.getLong("product_count"),
                        rs.getLong("total_units"),
                        rs.getBigDecimal("inventory_value"),
                        rs.getLong("out_of_stock_products"),
                        rs.getLong("low_stock_products")
                ),
                seller.getId()
        );

        if (!results.isEmpty()) {
            return results.get(0);
        }

        return new SellerInventorySummaryDTO(
                seller.getId(),
                0L,
                0L,
                BigDecimal.ZERO,
                0L,
                0L
        );
    }

    // ------------------------------------------------------------
    // LOW STOCK ALERTS
    // ------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<InventoryAlertDTO> getLowStockProducts() {

        User seller = getCurrentSeller();

        return jdbcTemplate.query(
                """
                SELECT
                    p.id AS product_id,
                    p.name AS product_name,
                    p.stock,
                    p.low_stock_threshold
                FROM products p
                WHERE p.seller_id = ?
                  AND p.stock <= p.low_stock_threshold
                ORDER BY
                    CASE WHEN p.stock = 0 THEN 0 ELSE 1 END,
                    p.stock ASC,
                    p.name ASC
                """,
                (rs, rowNum) -> new InventoryAlertDTO(
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getInt("stock"),
                        rs.getInt("low_stock_threshold"),
                        rs.getInt("stock") <= 0
                ),
                seller.getId()
        );
    }
    // ------------------------------------------------------------
    // INVENTORY MOVEMENT HISTORY
    // ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InventoryMovementDTO> getMovements(int requestedLimit) {

        User seller = getCurrentSeller();

        int limit = Math.max(1, Math.min(requestedLimit, 200));

        return jdbcTemplate.query(
                """
                SELECT
                    m.id,
                    m.product_id,
                    p.name AS product_name,
                    m.quantity_before,
                    m.quantity_after,
                    m.quantity_delta,
                    m.reason,
                    m.reference_type,
                    m.reference_id,
                    m.note,
                    m.created_at
                FROM inventory_movements m
                JOIN products p ON p.id = m.product_id
                WHERE m.seller_id = ?
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new InventoryMovementDTO(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getInt("quantity_before"),
                        rs.getInt("quantity_after"),
                        rs.getInt("quantity_delta"),
                        rs.getString("reason"),
                        rs.getString("reference_type"),
                        rs.getString("reference_id"),
                        rs.getString("note"),
                        rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                ),
                seller.getId(),
                limit
        );
    }

    // ------------------------------------------------------------
    // ADJUST INVENTORY
    // ------------------------------------------------------------

    @Transactional
    public InventoryMovementDTO adjustInventory(
            Long productId,
            InventoryAdjustmentRequest request) {

        if (request == null || request.quantityDelta() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Quantity adjustment is required."
            );
        }

        if (request.quantityDelta() == 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Quantity adjustment cannot be zero."
            );
        }

        User seller = getCurrentSeller();

        Product product = productRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Product not found."
                ));

        verifyOwnership(product, seller);

        int previousStock = product.getStock() == null
                ? 0
                : product.getStock();

        int updatedStock;

        try {
            updatedStock = Math.addExact(
                    previousStock,
                    request.quantityDelta()
            );
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Stock quantity exceeds the supported limit."
            );
        }

        if (updatedStock < 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Stock cannot become negative."
            );
        }

        // Configure the V8 PostgreSQL trigger in this transaction.
        // The trigger records the resulting inventory movement.
        setInventoryTriggerContext(
                request.reason(),
                seller.getId(),
                request.referenceType(),
                request.referenceId(),
                request.note()
        );

        product.setStock(updatedStock);

        productRepository.saveAndFlush(product);

        return jdbcTemplate.queryForObject(
                """
                SELECT
                    m.id,
                    m.product_id,
                    p.name AS product_name,
                    m.quantity_before,
                    m.quantity_after,
                    m.quantity_delta,
                    m.reason,
                    m.reference_type,
                    m.reference_id,
                    m.note,
                    m.created_at
                FROM inventory_movements m
                JOIN products p ON p.id = m.product_id
                WHERE m.product_id = ?
                  AND m.seller_id = ?
                ORDER BY m.id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new InventoryMovementDTO(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getInt("quantity_before"),
                        rs.getInt("quantity_after"),
                        rs.getInt("quantity_delta"),
                        rs.getString("reason"),
                        rs.getString("reference_type"),
                        rs.getString("reference_id"),
                        rs.getString("note"),
                        rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                ),
                productId,
                seller.getId()
        );
    }

    // ------------------------------------------------------------
    // UPDATE LOW STOCK THRESHOLD
    // ------------------------------------------------------------

    @Transactional
    public void updateLowStockThreshold(
            Long productId,
            LowStockThresholdRequest request) {

        if (request == null
                || request.threshold() == null
                || request.threshold() < 0) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "A non-negative stock threshold is required."
            );
        }

        User seller = getCurrentSeller();

        Product product = productRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Product not found."
                ));

        verifyOwnership(product, seller);

        int updated = jdbcTemplate.update(
                """
                UPDATE products
                SET low_stock_threshold = ?
                WHERE id = ?
                  AND seller_id = ?
                """,
                request.threshold(),
                productId,
                seller.getId()
        );

        if (updated != 1) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Product threshold could not be updated."
            );
        }
    }

    // ------------------------------------------------------------
    // POSTGRESQL V8 INVENTORY TRIGGER CONTEXT
    // ------------------------------------------------------------

    private void setInventoryTriggerContext(
        String reason,
        Long actorId,
        String referenceType,
        String referenceId,
        String note) {

    jdbcTemplate.query(
            """
            SELECT
                set_config('shopsphere.inventory_reason', ?, true),
                set_config('shopsphere.inventory_actor', ?, true),
                set_config('shopsphere.inventory_reference_type', ?, true),
                set_config('shopsphere.inventory_reference_id', ?, true),
                set_config('shopsphere.inventory_note', ?, true)
            """
            , (org.springframework.jdbc.core.RowCallbackHandler) rs -> { },
            reason.trim(),
            String.valueOf(actorId),
            safe(referenceType),
            safe(referenceId),
            safe(note)
    );
}
private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    // ------------------------------------------------------------
    // SELLER OWNERSHIP
    // ------------------------------------------------------------

    private void verifyOwnership(Product product, User seller) {

        if (product.getSeller() == null
                || !product.getSeller().getId().equals(seller.getId())) {

            throw new AccessDeniedException(
                    "You can only manage inventory for your own products."
            );
        }
    }

    // ------------------------------------------------------------
    // CURRENT AUTHENTICATED SELLER
    // ------------------------------------------------------------

    private User getCurrentSeller() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new AccessDeniedException(
                    "Authentication is required."
            );
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user was not found."
                ));

        if (!"SELLER".equalsIgnoreCase(user.getRole())) {
            throw new AccessDeniedException(
                    "Only sellers can access inventory management."
            );
        }

        return user;
    }
}