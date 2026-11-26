
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.SellerAnalyticsSummaryDTO;
import com.abrar.shopsphere.dto.SellerSalesPointDTO;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SellerAnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public SellerAnalyticsService(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    public List<SellerSalesPointDTO> getSales(String period, int days) {

        Long sellerId = getCurrentSellerId();

        String dateGrouping = switch (period.toUpperCase()) {
            case "DAILY" -> "day";
            case "WEEKLY" -> "week";
            case "MONTHLY" -> "month";
            default -> throw new IllegalArgumentException(
                    "Period must be DAILY, WEEKLY or MONTHLY"
            );
        };

        int safeDays = validateDays(days);

        String sql = """
                SELECT
                    DATE_TRUNC(?, sales_date::TIMESTAMP)::DATE AS period_start,
                    COALESCE(SUM(paid_orders), 0) AS paid_orders,
                    COALESCE(SUM(units_sold), 0) AS units_sold,
                    COALESCE(SUM(gross_sales), 0)::NUMERIC(18,2) AS gross_sales
                FROM seller_daily_sales
                WHERE seller_id = ?
                  AND sales_date >= CURRENT_DATE - (? * INTERVAL '1 day')
                  AND sales_date <= CURRENT_DATE
                GROUP BY 1
                ORDER BY period_start
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SellerSalesPointDTO(
                        rs.getObject("period_start", LocalDate.class),
                        rs.getLong("paid_orders"),
                        rs.getLong("units_sold"),
                        rs.getBigDecimal("gross_sales")
                ),
                dateGrouping,
                sellerId,
                safeDays
        );
    }

    public SellerAnalyticsSummaryDTO getSummary(int days) {

        Long sellerId = getCurrentSellerId();
        int safeDays = validateDays(days);

        String salesSql = """
                SELECT
                    COALESCE(SUM(paid_orders), 0) AS paid_orders,
                    COALESCE(SUM(units_sold), 0) AS units_sold,
                    COALESCE(SUM(gross_sales), 0)::NUMERIC(18,2) AS gross_sales
                FROM seller_daily_sales
                WHERE seller_id = ?
                  AND sales_date >= CURRENT_DATE - (? * INTERVAL '1 day')
                  AND sales_date <= CURRENT_DATE
                """;

        SellerAnalyticsSummaryDTO sales = jdbcTemplate.queryForObject(
                salesSql,
                (rs, rowNum) -> new SellerAnalyticsSummaryDTO(
                        rs.getLong("paid_orders"),
                        rs.getLong("units_sold"),
                        rs.getBigDecimal("gross_sales"),
                        BigDecimal.ZERO,
                        0L,
                        0L,
                        0L
                ),
                sellerId,
                safeDays
        );

        String inventorySql = """
                SELECT
                    product_count,
                    inventory_value,
                    low_stock_products,
                    out_of_stock_products
                FROM seller_inventory_summary
                WHERE seller_id = ?
                """;

        List<SellerAnalyticsSummaryDTO> inventoryResults =
                jdbcTemplate.query(
                        inventorySql,
                        (rs, rowNum) -> new SellerAnalyticsSummaryDTO(
                                0L,
                                0L,
                                BigDecimal.ZERO,
                                rs.getBigDecimal("inventory_value"),
                                rs.getLong("product_count"),
                                rs.getLong("low_stock_products"),
                                rs.getLong("out_of_stock_products")
                        ),
                        sellerId
                );

        if (sales == null) {
            throw new IllegalStateException(
                    "Unable to calculate seller sales summary"
            );
        }

        if (inventoryResults.isEmpty()) {
            return sales;
        }

        SellerAnalyticsSummaryDTO inventory = inventoryResults.get(0);

        return new SellerAnalyticsSummaryDTO(
                sales.paidOrders(),
                sales.unitsSold(),
                sales.grossSales(),
                inventory.inventoryValue(),
                inventory.productCount(),
                inventory.lowStockProducts(),
                inventory.outOfStockProducts()
        );
    }

    private int validateDays(int days) {
        if (days != 7 && days != 30 && days != 90 && days != 365) {
            throw new IllegalArgumentException(
                    "Days must be one of: 7, 30, 90 or 365"
            );
        }

        return days;
    }

    private Long getCurrentSellerId() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new IllegalStateException("Authentication required");
        }

        User seller = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated seller not found"
                        )
                );

        return seller.getId();
    }
}
