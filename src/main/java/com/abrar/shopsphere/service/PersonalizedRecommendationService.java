
package com.abrar.shopsphere.ai.service;

import com.abrar.shopsphere.ai.dto.PersonalizedRecommendationDTO;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PersonalizedRecommendationService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public PersonalizedRecommendationService(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PersonalizedRecommendationDTO> getRecommendations(
            int requestedLimit) {

        Long customerId = getAuthenticatedCustomerId();

        int limit = Math.max(1, Math.min(requestedLimit, 20));

        String sql = """
            WITH customer_paid_orders AS (
                SELECT
                    o.id AS order_id,
                    pay.created_at AS paid_at
                FROM orders o
                INNER JOIN payments pay
                    ON pay.order_id = o.id
                WHERE o.user_id = ?
                  AND UPPER(o.status) = 'PAID'
                  AND UPPER(pay.status) = 'SUCCESS'
            ),

            customer_purchased_products AS (
                SELECT
                    oi.product_id,
                    SUM(oi.quantity) AS purchased_quantity
                FROM order_items oi
                INNER JOIN customer_paid_orders po
                    ON po.order_id = oi.order_id
                GROUP BY oi.product_id
            ),

            customer_cart_products AS (
                SELECT DISTINCT ci.product_id
                FROM carts c
                INNER JOIN cart_items ci
                    ON ci.cart_id = c.id
                WHERE c.user_id = ?
            ),

            customer_category_affinity AS (
                SELECT
                    p.category_id,
                    SUM(
                        oi.quantity *
                        EXP(
                            -GREATEST(
                                EXTRACT(
                                    EPOCH FROM (
                                        CURRENT_TIMESTAMP - po.paid_at
                                    )
                                ) / 86400.0,
                                0
                            ) / 60.0
                        )
                    ) AS category_score
                FROM order_items oi
                INNER JOIN customer_paid_orders po
                    ON po.order_id = oi.order_id
                INNER JOIN products p
                    ON p.id = oi.product_id
                WHERE p.category_id IS NOT NULL
                GROUP BY p.category_id
            ),

            customer_cart_categories AS (
                SELECT
                    p.category_id,
                    SUM(ci.quantity) AS cart_category_score
                FROM carts c
                INNER JOIN cart_items ci
                    ON ci.cart_id = c.id
                INNER JOIN products p
                    ON p.id = ci.product_id
                WHERE c.user_id = ?
                  AND p.category_id IS NOT NULL
                GROUP BY p.category_id
            ),

            co_purchased AS (
                SELECT
                    other_item.product_id,

                    SUM(
                        LEAST(
                            source_item.quantity,
                            other_item.quantity
                        ) *
                        EXP(
                            -GREATEST(
                                EXTRACT(
                                    EPOCH FROM (
                                        CURRENT_TIMESTAMP - po.paid_at
                                    )
                                ) / 86400.0,
                                0
                            ) / 60.0
                        )
                    ) AS co_score

                FROM customer_paid_orders po

                INNER JOIN order_items source_item
                    ON source_item.order_id = po.order_id

                INNER JOIN customer_purchased_products purchased
                    ON purchased.product_id = source_item.product_id

                INNER JOIN order_items other_item
                    ON other_item.order_id = po.order_id

                WHERE other_item.product_id <> source_item.product_id

                GROUP BY other_item.product_id
            ),

            popularity AS (
                SELECT
                    oi.product_id,

                    SUM(
                        oi.quantity *
                        EXP(
                            -GREATEST(
                                EXTRACT(
                                    EPOCH FROM (
                                        CURRENT_TIMESTAMP - pay.created_at
                                    )
                                ) / 86400.0,
                                0
                            ) / 30.0
                        )
                    ) AS popularity_score

                FROM order_items oi

                INNER JOIN orders o
                    ON o.id = oi.order_id

                INNER JOIN payments pay
                    ON pay.order_id = o.id

                WHERE UPPER(o.status) = 'PAID'
                  AND UPPER(pay.status) = 'SUCCESS'

                GROUP BY oi.product_id
            ),

            candidates AS (
                SELECT
                    p.id,
                    p.name,
                    p.description,
                    p.price,
                    p.stock,
                    p.category_id,
                    p.seller_id,
                    c.name AS category_name,
                    p.image_url,

                    COALESCE(cp.co_score, 0) AS co_score,

                    COALESCE(
                        ca.category_score, 0
                    ) AS category_score,

                    COALESCE(
                        ccc.cart_category_score, 0
                    ) AS cart_category_score,

                    COALESCE(
                        pop.popularity_score, 0
                    ) AS popularity_score

                FROM products p

                LEFT JOIN categories c
                    ON c.id = p.category_id

                LEFT JOIN co_purchased cp
                    ON cp.product_id = p.id

                LEFT JOIN customer_category_affinity ca
                    ON ca.category_id = p.category_id

                LEFT JOIN customer_cart_categories ccc
                    ON ccc.category_id = p.category_id

                LEFT JOIN popularity pop
                    ON pop.product_id = p.id

                WHERE p.stock > 0

                  AND NOT EXISTS (
                      SELECT 1
                      FROM customer_purchased_products purchased
                      WHERE purchased.product_id = p.id
                  )

                  AND NOT EXISTS (
                      SELECT 1
                      FROM customer_cart_products cart_product
                      WHERE cart_product.product_id = p.id
                  )
            ),

            scored AS (
                SELECT
                    *,

                    (
                        0.45 * LN(1 + co_score)
                        +
                        0.25 * LN(1 + category_score)
                        +
                        0.15 * LN(1 + cart_category_score)
                        +
                        0.15 * LN(1 + popularity_score)
                    ) AS raw_score

                FROM candidates
            ),

            diversified AS (
                SELECT
                    *,

                    ROW_NUMBER() OVER (
                        PARTITION BY category_id
                        ORDER BY raw_score DESC, id DESC
                    ) AS category_position

                FROM scored
            ),

            ranked AS (
                SELECT
                    *,

                    raw_score /
                    (
                        1 + 0.18 * (category_position - 1)
                    ) AS final_score

                FROM diversified
            )

            SELECT
                id,
                name,
                description,
                price,
                stock,
                category_name,
                image_url,
                final_score AS recommendation_score,

                CASE
                    WHEN co_score >= category_score
                         AND co_score >= cart_category_score
                         AND co_score >= popularity_score
                         AND co_score > 0
                        THEN 'Frequently bought with products you purchased'

                    WHEN category_score >= cart_category_score
                         AND category_score >= popularity_score
                         AND category_score > 0
                        THEN 'Based on your recent purchase interests'

                    WHEN cart_category_score >= popularity_score
                         AND cart_category_score > 0
                        THEN 'Based on products in your cart'

                    ELSE 'Trending among ShopSphere customers'
                END AS recommendation_reason

            FROM ranked

            ORDER BY
                final_score DESC,
                id DESC

            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PersonalizedRecommendationDTO(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getObject("price", Double.class),
                        rs.getObject("stock", Integer.class),
                        rs.getString("category_name"),
                        rs.getString("image_url"),
                        rs.getObject(
                                "recommendation_score",
                                Double.class
                        ),
                        rs.getString("recommendation_reason")
                ),
                customerId,
                customerId,
                customerId,
                limit
        );
    }

    private Long getAuthenticatedCustomerId() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new AccessDeniedException(
                    "Please log in to view personalized recommendations."
            );
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated customer was not found."
                ));
    }
}
