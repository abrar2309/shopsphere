
package com.abrar.shopsphere.security;

import com.abrar.shopsphere.config.RateLimitProperties;
import com.abrar.shopsphere.dto.ProductRequestDTO;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;
import com.abrar.shopsphere.service.OutboxService;
import com.abrar.shopsphere.service.ProductService;
import com.abrar.shopsphere.service.RateLimitService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ProductServiceSecurityTest.TestConfig.class)
class ProductServiceSecurityTest {

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import({
            ProductService.class,
            ProductSecurity.class
    })
    static class TestConfig {

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        StringRedisTemplate redisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RateLimitService rateLimitService() {
            return mock(RateLimitService.class);
        }

        @Bean
        RateLimitProperties rateLimitProperties() {
            return mock(RateLimitProperties.class);
        }

        @Bean
        OutboxService outboxService() {
            return mock(OutboxService.class);
        }
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxService outboxService;

    private User sellerA;
    private User sellerB;
    private User admin;
    private Product product;

    @BeforeEach
    void setUp() {

        reset(
                productRepository,
                userRepository,
                outboxService
        );

        SecurityContextHolder.clearContext();

        sellerA = createUser(
                1L,
                "sellerA@test.com",
                "SELLER"
        );

        sellerB = createUser(
                2L,
                "sellerB@test.com",
                "SELLER"
        );

        admin = createUser(
                3L,
                "admin@test.com",
                "ADMIN"
        );

        product = new Product();

        product.setId(100L);
        product.setName("Test Laptop");
        product.setDescription("A laptop for security testing");
        product.setPrice(50000.0);
        product.setStock(10);
        product.setSeller(sellerA);
    }

    private User createUser(
            Long id,
            String email,
            String role
    ) {

        User user = new User();

        user.setId(id);
        user.setEmail(email);
        user.setRole(role);

        return user;
    }

    private void authenticate(String email) {

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        email,
                        "password",
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private ProductRequestDTO createProductRequest() {

        ProductRequestDTO dto = new ProductRequestDTO();

        dto.setName("Updated Laptop");
        dto.setDescription("Updated laptop description");
        dto.setPrice(55000.0);
        dto.setStock(15);

        return dto;
    }

    // ============================================================
    // TEST 1: SELLER CAN UPDATE OWN PRODUCT
    // ============================================================

    @Test
    void sellerCanUpdateOwnProduct() {

        authenticate("sellerA@test.com");

        when(userRepository.findByEmail("sellerA@test.com"))
                .thenReturn(Optional.of(sellerA));

        // Ownership validation lookup.
        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        // ProductService update locking lookup.
        when(productRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenReturn(product);

        assertDoesNotThrow(() ->
                productService.updateProduct(
                        100L,
                        createProductRequest()
                )
        );

        verify(productRepository).save(product);

        verify(outboxService).saveProductCacheInvalidationEvent(
                100L,
                "UPDATED"
        );
    }

    // ============================================================
    // TEST 2: SELLER CANNOT UPDATE ANOTHER SELLER'S PRODUCT
    // ============================================================

    @Test
    void sellerCannotUpdateAnotherSellersProduct() {

        authenticate("sellerB@test.com");

        when(userRepository.findByEmail("sellerB@test.com"))
                .thenReturn(Optional.of(sellerB));

        // Ownership validation must find the other seller's product.
        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        assertThrows(
                AccessDeniedException.class,
                () -> productService.updateProduct(
                        100L,
                        createProductRequest()
                )
        );

        verify(productRepository, never())
                .findByIdForUpdate(100L);

        verify(productRepository, never())
                .save(any(Product.class));

        verifyNoInteractions(outboxService);
    }

    // ============================================================
    // TEST 3: SELLER CANNOT DELETE ANOTHER SELLER'S PRODUCT
    // ============================================================

    @Test
    void sellerCannotDeleteAnotherSellersProduct() {

        authenticate("sellerB@test.com");

        when(userRepository.findByEmail("sellerB@test.com"))
                .thenReturn(Optional.of(sellerB));

        when(productRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(product));

        assertThrows(
                AccessDeniedException.class,
                () -> productService.deleteProduct(100L)
        );

        verify(productRepository, never())
                .delete(any(Product.class));

        verifyNoInteractions(outboxService);
    }

    // ============================================================
    // TEST 4: ADMIN CAN UPDATE ANOTHER SELLER'S PRODUCT
    // ============================================================

    @Test
    void adminCanUpdateAnotherSellersProduct() {

        authenticate("admin@test.com");

        when(userRepository.findByEmail("admin@test.com"))
                .thenReturn(Optional.of(admin));

        // Admin bypasses the seller ownership lookup.
        // ProductService still needs the locking lookup.
        when(productRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenReturn(product);

        assertDoesNotThrow(() ->
                productService.updateProduct(
                        100L,
                        createProductRequest()
                )
        );

        verify(productRepository, never())
                .findById(100L);

        verify(productRepository).findByIdForUpdate(100L);

        verify(productRepository).save(product);

        verify(outboxService).saveProductCacheInvalidationEvent(
                100L,
                "UPDATED"
        );
    }
}
