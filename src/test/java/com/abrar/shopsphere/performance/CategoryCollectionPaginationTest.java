
package com.abrar.shopsphere.performance;

import com.abrar.shopsphere.dto.CategoryWithProductsResponseDTO;
import com.abrar.shopsphere.entity.Category;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.service.CategoryService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
})
@Transactional
class CategoryCollectionPaginationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private CategoryService categoryService;

    private Statistics statistics;

    @BeforeEach
    void setUp() {

        SessionFactory sessionFactory =
                entityManagerFactory.unwrap(SessionFactory.class);

        statistics = sessionFactory.getStatistics();
    }

    @Test
    void shouldFetchPaginatedCategoriesWithProductsWithoutCollectionPagination() {

        // STEP 1: Create categories
        Category electronics = createCategory("Electronics");
        Category books = createCategory("Books");
        Category furniture = createCategory("Furniture");

        // STEP 2: Electronics products
        createProduct("Laptop", electronics);
        createProduct("Keyboard", electronics);
        createProduct("Mouse", electronics);

        // STEP 3: Books products
        createProduct("Java Book", books);
        createProduct("SQL Book", books);

        // STEP 4: Furniture products
        createProduct("Office Chair", furniture);
        createProduct("Office Desk", furniture);
        createProduct("Bookshelf", furniture);
        createProduct("Dining Table", furniture);

        entityManager.flush();
        entityManager.clear();

        // Ignore setup SQL statements
        statistics.clear();

        // STEP 5: Fetch first page
        Page<CategoryWithProductsResponseDTO> firstPage =
                categoryService.getCategoriesWithProducts(0, 2);

        long firstPageSqlCount =
                statistics.getPrepareStatementCount();

        System.out.println("\n====================================");
        System.out.println("CATEGORY COLLECTION FETCH TEST");
        System.out.println("====================================");

        System.out.println(
                "First page SQL statements: " + firstPageSqlCount
        );

        // Verify pagination
        assertEquals(2, firstPage.getContent().size());
        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());

        // Verify category ordering
        assertEquals(
                "Electronics",
                firstPage.getContent().get(0).getName()
        );

        assertEquals(
                "Books",
                firstPage.getContent().get(1).getName()
        );

        // Verify collection fetching
        assertEquals(
                3,
                firstPage.getContent().get(0).getProducts().size()
        );

        assertEquals(
                2,
                firstPage.getContent().get(1).getProducts().size()
        );

        // Expected: ID query + count query + collection fetch
        assertEquals(
                3,
                firstPageSqlCount,
                "Expected three SQL statements for first page"
        );

        // STEP 6: Fetch second page
        statistics.clear();

        Page<CategoryWithProductsResponseDTO> secondPage =
                categoryService.getCategoriesWithProducts(1, 2);

        long secondPageSqlCount =
                statistics.getPrepareStatementCount();

        System.out.println(
                "Second page SQL statements: " + secondPageSqlCount
        );

        assertEquals(1, secondPage.getContent().size());

        assertEquals(
                "Furniture",
                secondPage.getContent().get(0).getName()
        );

        assertEquals(
                4,
                secondPage.getContent().get(0).getProducts().size()
        );

        assertEquals(3, secondPage.getTotalElements());
        assertEquals(2, secondPage.getTotalPages());

        System.out.println("------------------------------------");
        System.out.println("Page 0: 2 categories, 5 products");
        System.out.println("Page 1: 1 category, 4 products");
        System.out.println("Total categories: 3");
        System.out.println("Total products: 9");
        System.out.println("====================================");
        System.out.println("COLLECTION FETCH TEST PASSED");
        System.out.println("====================================");
    }

    private Category createCategory(String name) {

        Category category = new Category();
        category.setName(name);

        entityManager.persist(category);

        return category;
    }

    private void createProduct(
            String name,
            Category category) {

        Product product = new Product();

        product.setName(name);
        product.setDescription(
                "Performance testing product"
        );
        product.setPrice(100.0);
        product.setStock(10);

        // Product owns the relationship
        product.setCategory(category);

        entityManager.persist(product);
    }
}
