
package com.abrar.shopsphere.performance;

import com.abrar.shopsphere.dto.ProductCatalogResponseDTO;
import com.abrar.shopsphere.entity.Category;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.ProductRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductCatalogIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;


    @Test
    void shouldReturnOptimizedProductCatalogWithPaginationAndNullableRelationships() {

        // ========================================================
        // 1. CREATE TEST SELLERS
        // ========================================================

        User sellerA = new User(
                null,
                "John",
                "Seller",
                "john.seller@test.com",
                "9000000001",
                "test-password",
                "SELLER"
        );

        User sellerB = new User(
                null,
                "Jane",
                "Seller",
                "jane.seller@test.com",
                "9000000002",
                "test-password",
                "SELLER"
        );

        entityManager.persist(sellerA);
        entityManager.persist(sellerB);


        // ========================================================
        // 2. CREATE TEST CATEGORIES
        // ========================================================

        Category electronics = new Category();
        electronics.setName("Electronics");

        Category books = new Category();
        books.setName("Books");

        entityManager.persist(electronics);
        entityManager.persist(books);


        // ========================================================
        // 3. CREATE FOUR PRODUCTS
        // ========================================================

        // Product 1: Has category and seller.
        Product laptop = createProduct(
                "Laptop",
                "Business laptop for testing",
                1000.0,
                electronics,
                sellerA
        );

        // Product 2: Has seller but no category.
        Product keyboard = createProduct(
                "Keyboard",
                "Mechanical keyboard for testing",
                200.0,
                null,
                sellerB
        );

        // Product 3: Has category but no seller.
        Product javaBook = createProduct(
                "Java Book",
                "Complete Java programming guide",
                500.0,
                books,
                null
        );

        // Product 4: Has neither category nor seller.
        Product mouse = createProduct(
                "Mouse",
                "Wireless mouse for everyday use",
                100.0,
                null,
                null
        );

        entityManager.persist(laptop);
        entityManager.persist(keyboard);
        entityManager.persist(javaBook);
        entityManager.persist(mouse);

        entityManager.flush();
        entityManager.clear();


        // ========================================================
        // 4. TEST FIRST PAGE - SORT BY PRICE
        // ========================================================

        Page<ProductCatalogResponseDTO> firstPage =
                productRepository.findCatalogPage(
                        PageRequest.of(
                                0,
                                2,
                                Sort.by("price").ascending()
                        )
                );

        assertEquals(4, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(2, firstPage.getContent().size());

        // Lowest price should come first.
        assertEquals("Mouse", firstPage.getContent().get(0).getName());
        assertEquals(100.0, firstPage.getContent().get(0).getPrice());

        // Second-lowest price.
        assertEquals("Keyboard", firstPage.getContent().get(1).getName());
        assertEquals(200.0, firstPage.getContent().get(1).getPrice());


        // ========================================================
        // 5. VERIFY NULL RELATIONSHIP HANDLING
        // ========================================================

        ProductCatalogResponseDTO keyboardDTO =
                firstPage.getContent().get(1);

        assertNull(keyboardDTO.getCategoryName());
        assertEquals(
                "jane.seller@test.com",
                keyboardDTO.getSellerEmail()
        );


        // ========================================================
        // 6. TEST SECOND PAGE
        // ========================================================

        Page<ProductCatalogResponseDTO> secondPage =
                productRepository.findCatalogPage(
                        PageRequest.of(
                                1,
                                2,
                                Sort.by("price").ascending()
                        )
                );

        assertEquals(4, secondPage.getTotalElements());
        assertEquals(2, secondPage.getTotalPages());
        assertEquals(2, secondPage.getContent().size());

        assertEquals(
                "Java Book",
                secondPage.getContent().get(0).getName()
        );

        assertEquals(
                "Laptop",
                secondPage.getContent().get(1).getName()
        );


        // ========================================================
        // 7. VERIFY CATEGORY AND SELLER DATA
        // ========================================================

        ProductCatalogResponseDTO javaBookDTO =
                secondPage.getContent().get(0);

        assertEquals("Books", javaBookDTO.getCategoryName());
        assertNull(javaBookDTO.getSellerEmail());

        ProductCatalogResponseDTO laptopDTO =
                secondPage.getContent().get(1);

        assertEquals(
                "Electronics",
                laptopDTO.getCategoryName()
        );

        assertEquals(
                "john.seller@test.com",
                laptopDTO.getSellerEmail()
        );


        // ========================================================
        // 8. TEST EMPTY PAGE
        // ========================================================

        Page<ProductCatalogResponseDTO> emptyPage =
                productRepository.findCatalogPage(
                        PageRequest.of(
                                2,
                                2,
                                Sort.by("price").ascending()
                        )
                );

        assertTrue(emptyPage.getContent().isEmpty());
        assertEquals(4, emptyPage.getTotalElements());
        assertEquals(2, emptyPage.getTotalPages());


        // ========================================================
        // 9. PRINT TEST RESULTS
        // ========================================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("PRODUCT CATALOG INTEGRATION TEST");
        System.out.println("======================================");

        System.out.println(
                "Total products: " + firstPage.getTotalElements()
        );

        System.out.println(
                "Total pages: " + firstPage.getTotalPages()
        );

        System.out.println(
                "First page products: "
                        + firstPage.getContent().size()
        );

        System.out.println(
                "Second page products: "
                        + secondPage.getContent().size()
        );

        System.out.println(
                "Null category handling: PASSED"
        );

        System.out.println(
                "Null seller handling: PASSED"
        );

        System.out.println(
                "Price sorting: PASSED"
        );

        System.out.println(
                "Pagination: PASSED"
        );

        System.out.println(
                "Empty page handling: PASSED"
        );

        System.out.println("======================================");
        System.out.println("PRODUCT CATALOG TEST PASSED");
        System.out.println("======================================");
    }


    // ============================================================
    // HELPER METHOD
    // ============================================================

    private Product createProduct(
            String name,
            String description,
            Double price,
            Category category,
            User seller) {

        Product product = new Product();

        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStock(100);
        product.setCategory(category);
        product.setSeller(seller);

        return product;
    }
}
