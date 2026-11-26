package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.AdminDashboardSummaryDTO;
import com.abrar.shopsphere.dto.AdminPaymentHistoryDTO;
import com.abrar.shopsphere.dto.AdminOrderDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.abrar.shopsphere.dto.AdminOrderDTO;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminDashboardSummaryDTO getSummary() {

        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM users) AS total_users,
                    (SELECT COUNT(*) FROM users
                        WHERE UPPER(role) IN ('CUSTOMER', 'ROLE_CUSTOMER')
                    ) AS total_customers,
                    (SELECT COUNT(*) FROM users
                        WHERE UPPER(role) IN ('SELLER', 'ROLE_SELLER')
                    ) AS total_sellers,
                    (SELECT COUNT(*) FROM products) AS total_products,
                    (SELECT COUNT(*) FROM categories) AS total_categories,
                    (SELECT COUNT(*) FROM orders) AS total_orders,
                    (SELECT COUNT(*) FROM payments
                        WHERE UPPER(status) = 'PENDING'
                    ) AS pending_payments,
                    (SELECT COALESCE(SUM(amount), 0)
                        FROM payments
                        WHERE UPPER(status) = 'SUCCESS'
                    ) AS successful_revenue
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new AdminDashboardSummaryDTO(
                        rs.getLong("total_users"),
                        rs.getLong("total_customers"),
                        rs.getLong("total_sellers"),
                        rs.getLong("total_products"),
                        rs.getLong("total_categories"),
                        rs.getLong("total_orders"),
                        rs.getLong("pending_payments"),
                        rs.getBigDecimal("successful_revenue")
                )
        );
    }

    public List<AdminPaymentHistoryDTO> getPaymentHistory(int limit) {

        int safeLimit = Math.max(1, Math.min(limit, 200));

        String sql = """
                SELECT
                    h.id,
                    h.payment_id,
                    h.order_id,
                    h.old_status,
                    h.new_status,
                    h.payment_method,
                    h.admin_note,
                    CASE
                        WHEN u.id IS NULL THEN NULL
                        ELSE CONCAT_WS(' ', u.first_name, u.last_name)
                    END AS verified_by,
                    h.verified_at,
                    h.changed_at
                FROM payment_verification_history h
                LEFT JOIN users u ON u.id = h.verified_by
                ORDER BY h.changed_at DESC, h.id DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new AdminPaymentHistoryDTO(
                        rs.getLong("id"),
                        rs.getLong("payment_id"),
                        rs.getObject("order_id", Long.class),
                        rs.getString("old_status"),
                        rs.getString("new_status"),
                        rs.getString("payment_method"),
                        rs.getString("admin_note"),
                        rs.getString("verified_by"),
                        rs.getObject("verified_at",
                                java.time.OffsetDateTime.class),
                        rs.getObject("changed_at",
                                java.time.OffsetDateTime.class)
                ),
                safeLimit
        );
    }
    public List<AdminOrderDTO> getRecentOrders(int limit) {

        int safeLimit = Math.max(1, Math.min(limit, 200));

        String sql = """
            SELECT
                o.id AS order_id,
                u.id AS customer_id,
                CONCAT_WS(' ', u.first_name, u.last_name) AS customer_name,
                u.email AS customer_email,
                o.total_amount,
                o.status AS order_status,
                COUNT(DISTINCT oi.id) AS item_count,
                p.status AS payment_status,
                p.payment_method
            FROM orders o
            JOIN users u ON u.id = o.user_id
            LEFT JOIN order_items oi ON oi.order_id = o.id
            LEFT JOIN payments p ON p.order_id = o.id
            GROUP BY
                o.id, u.id, u.first_name, u.last_name, u.email,
                o.total_amount, o.status, p.status, p.payment_method
            ORDER BY o.id DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new AdminOrderDTO(
                        rs.getLong("order_id"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_name"),
                        rs.getString("customer_email"),
                        rs.getObject("total_amount", Double.class),
                        rs.getString("order_status"),
                        rs.getLong("item_count"),
                        rs.getString("payment_status"),
                        rs.getString("payment_method")
                ),
                safeLimit
        );
    }

}