
package com.abrar.shopsphere.performance;

import com.abrar.shopsphere.entity.Category;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.ProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class ProductFetchPerformanceTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldMeasureProductAndRelationshipQueries() {

        // ========================================================
        // STEP 1 - CREATE CATEGORIES
        // ========================================================

        Category category1 =
                new Category(null, "Electronics");

        Category category2 =
                new Category(null, "Books");

        Category category3 =
                new Category(null, "Furniture");

        entityManager.persist(category1);
        entityManager.persist(category2);
        entityManager.persist(category3);

        // ========================================================
        // STEP 2 - CREATE SELLERS
        // ========================================================

        User seller1 =
                createSeller("seller1@example.com");

        User seller2 =
                createSeller("seller2@example.com");

        User seller3 =
                createSeller("seller3@example.com");

        entityManager.persist(seller1);
        entityManager.persist(seller2);
        entityManager.persist(seller3);

        // ========================================================
        // STEP 3 - CREATE PRODUCTS
        // ========================================================

        Product product1 = createProduct(
                "Laptop",
                category1,
                seller1
        );

        Product product2 = createProduct(
                "Java Programming Book",
                category2,
                seller2
        );

        Product product3 = createProduct(
                "Office Chair",
                category3,
                seller3
        );

        entityManager.persist(product1);
        entityManager.persist(product2);
        entityManager.persist(product3);

        entityManager.flush();
        entityManager.clear();

        // ========================================================
        // STEP 4 - INITIALIZE HIBERNATE STATISTICS
        // ========================================================

        SessionFactory sessionFactory =
                entityManagerFactory.unwrap(SessionFactory.class);

        Statistics statistics =
                sessionFactory.getStatistics();

        statistics.clear();

        // ========================================================
        // STEP 5 - PRODUCT FETCH
        // ========================================================

        List<Product> products =
                productRepository.findAll();

        long queriesAfterProductFetch =
                statistics.getPrepareStatementCount();

        assertEquals(3, products.size());

        System.out.println();
        System.out.println("==================================");
        System.out.println("PRODUCT FETCH");
        System.out.println("==================================");

        System.out.println(
                "SQL statements after fetching products: "
                        + queriesAfterProductFetch
        );

        // ========================================================
        // STEP 6 - ACCESS EAGER CATEGORIES
        // ========================================================

        for (Product product : products) {

            assertNotNull(product.getCategory());

            System.out.println(
                    "Category: "
                            + product.getCategory().getName()
            );
        }

        long queriesAfterCategoryAccess =
                statistics.getPrepareStatementCount();

        // ========================================================
        // STEP 7 - ACCESS LAZY SELLERS WITH BATCH FETCHING
        // ========================================================

        for (Product product : products) {

            assertNotNull(product.getSeller());

            System.out.println(
                    "Seller email: "
                            + product.getSeller().getEmail()
            );
        }

        long queriesAfterSellerAccess =
                statistics.getPrepareStatementCount();

        long categoryQueries =
                queriesAfterCategoryAccess
                        - queriesAfterProductFetch;

        long sellerQueries =
                queriesAfterSellerAccess
                        - queriesAfterCategoryAccess;

        // ========================================================
        // STEP 8 - BATCH FETCHING ANALYSIS
        // ========================================================

        System.out.println();
        System.out.println("==================================");
        System.out.println("HIBERNATE BATCH FETCHING ANALYSIS");
        System.out.println("==================================");

        System.out.println(
                "Product fetch queries: "
                        + queriesAfterProductFetch
        );

        System.out.println(
                "Additional category queries: "
                        + categoryQueries
        );

        System.out.println(
                "Additional seller queries: "
                        + sellerQueries
        );

        System.out.println(
                "Total queries: "
                        + queriesAfterSellerAccess
        );

        System.out.println("==================================");

        // Three seller proxies should be loaded in one batch.
        assertEquals(
                1,
                sellerQueries,
                "Expected Hibernate to load three sellers in one batch"
        );

        // ========================================================
        // STEP 9 - ENTITY GRAPH OPTIMIZATION
        // ========================================================

        entityManager.clear();
        statistics.clear();

        List<Product> optimizedProducts =
                productRepository.findAllWithCategoryAndSeller();

        long queriesAfterOptimizedFetch =
                statistics.getPrepareStatementCount();

        for (Product product : optimizedProducts) {

            assertNotNull(product.getCategory());
            assertNotNull(product.getSeller());

            System.out.println(
                    "Optimized category: "
                            + product.getCategory().getName()
            );

            System.out.println(
                    "Optimized seller: "
                            + product.getSeller().getEmail()
            );
        }

        long queriesAfterOptimizedAccess =
                statistics.getPrepareStatementCount();

        System.out.println();
        System.out.println("==================================");
        System.out.println("ENTITY GRAPH OPTIMIZATION");
        System.out.println("==================================");

        System.out.println(
                "Queries after optimized fetch: "
                        + queriesAfterOptimizedFetch
        );

        System.out.println(
                "Queries after relationship access: "
                        + queriesAfterOptimizedAccess
        );

        System.out.println(
                "Additional relationship queries: "
                        + (queriesAfterOptimizedAccess
                        - queriesAfterOptimizedFetch)
        );

        System.out.println("==================================");

        assertEquals(3, optimizedProducts.size());
        assertEquals(1, queriesAfterOptimizedFetch);
        assertEquals(1, queriesAfterOptimizedAccess);

        // ========================================================
        // STEP 10 - JOIN FETCH OPTIMIZATION
        // ========================================================

        entityManager.clear();
        statistics.clear();

        List<Product> joinFetchProducts =
                productRepository.findAllWithJoinFetch();

        long queriesAfterJoinFetch =
                statistics.getPrepareStatementCount();

        for (Product product : joinFetchProducts) {

            assertNotNull(product.getCategory());
            assertNotNull(product.getSeller());

            System.out.println(
                    "JOIN FETCH category: "
                            + product.getCategory().getName()
            );

            System.out.println(
                    "JOIN FETCH seller: "
                            + product.getSeller().getEmail()
            );
        }

        long queriesAfterJoinFetchAccess =
                statistics.getPrepareStatementCount();

        System.out.println();
        System.out.println("==================================");
        System.out.println("JOIN FETCH OPTIMIZATION");
        System.out.println("==================================");

        System.out.println(
                "Products fetched: "
                        + joinFetchProducts.size()
        );

        System.out.println(
                "Queries after JOIN FETCH: "
                        + queriesAfterJoinFetch
        );

        System.out.println(
                "Queries after relationship access: "
                        + queriesAfterJoinFetchAccess
        );

        System.out.println(
                "Additional relationship queries: "
                        + (queriesAfterJoinFetchAccess
                        - queriesAfterJoinFetch)
        );

        System.out.println("==================================");

        assertEquals(3, joinFetchProducts.size());
        assertEquals(1, queriesAfterJoinFetch);
        assertEquals(1, queriesAfterJoinFetchAccess);

        // ========================================================
        // STEP 11 - DTO PROJECTION OPTIMIZATION
        // ========================================================

        entityManager.clear();
        statistics.clear();

        var projectedProducts =
                productRepository.findAllProjected(
                        PageRequest.of(0, 10)
                );

        long queriesAfterProjection =
                statistics.getPrepareStatementCount();

        System.out.println();
        System.out.println("==================================");
        System.out.println("DTO PROJECTION OPTIMIZATION");
        System.out.println("==================================");

        System.out.println(
                "Products fetched: "
                        + projectedProducts.getNumberOfElements()
        );

        System.out.println(
                "SQL statements: "
                        + queriesAfterProjection
        );

        projectedProducts.getContent().forEach(dto -> {

            System.out.println(
                    dto.getId() + " | "
                            + dto.getName() + " | "
                            + dto.getDescription() + " | "
                            + dto.getPrice()
            );
        });

        System.out.println("==================================");

        assertEquals(
                3,
                projectedProducts.getNumberOfElements()
        );
    }

    // ============================================================
    // HELPER METHOD - CREATE SELLER
    // ============================================================

    private User createSeller(String email) {

        User seller = new User();

        seller.setFirstName("Test");
        seller.setLastName("Seller");
        seller.setEmail(email);
        seller.setPhoneNumber("9999999999");
        seller.setPassword("test-password");
        seller.setRole("SELLER");

        return seller;
    }

    // ============================================================
    // HELPER METHOD - CREATE PRODUCT
    // ============================================================

    private Product createProduct(
            String name,
            Category category,
            User seller
    ) {

        Product product = new Product();

        product.setName(name);

        product.setDescription(
                "Performance testing product description"
        );

        product.setPrice(1000.0);
        product.setStock(10);
        product.setCategory(category);
        product.setSeller(seller);

        return product;
    }
}
