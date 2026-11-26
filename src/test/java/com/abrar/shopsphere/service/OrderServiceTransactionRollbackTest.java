
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.OrderItemRequestDTO;
import com.abrar.shopsphere.dto.OrderRequestDTO;
import com.abrar.shopsphere.dto.RateLimitResult;

import com.abrar.shopsphere.entity.Address;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;

import com.abrar.shopsphere.repository.AddressRepository;
import com.abrar.shopsphere.repository.OrderRepository;
import com.abrar.shopsphere.repository.OutboxEventRepository;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ActiveProfiles("test")
@SpringBootTest
class OrderServiceTransactionRollbackTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AddressRepository addressRepository;

    @MockitoBean
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean(
            name = "rateLimitService",
            enforceOverride = true
    )
    private RateLimitService rateLimitService;

    private User user;
    private Product product;
    private Address deliveryAddress;
    private long initialOrderCount;

    @BeforeEach
    void setUp() {

        SecurityContextHolder.clearContext();

        String email = "rollback-" + UUID.randomUUID()
                + "@shopsphere.test";

        // CREATE TEST USER
        user = new User();

        user.setFirstName("Test");
        user.setLastName("Customer");
        user.setEmail(email);
        user.setPhoneNumber("9000000000");
        user.setPassword("test-password");
        user.setRole("ROLE_USER");

        user = userRepository.saveAndFlush(user);

        // CREATE DELIVERY ADDRESS FOR THIS USER
        deliveryAddress = new Address();

        deliveryAddress.setUser(user);
        deliveryAddress.setLabel("Home");
        deliveryAddress.setRecipientName("Test Customer");
        deliveryAddress.setPhoneNumber("9000000000");
        deliveryAddress.setAddressLine1("123 Test Street");
        deliveryAddress.setAddressLine2("Near Test Park");
        deliveryAddress.setCity("Bengaluru");
        deliveryAddress.setState("Karnataka");
        deliveryAddress.setPostalCode("560001");
        deliveryAddress.setCountry("India");
        deliveryAddress.setDefault(true);

        deliveryAddress = addressRepository.saveAndFlush(deliveryAddress);

        // CREATE TEST PRODUCT
        product = new Product();

        product.setName("Rollback Test Product");

        product.setDescription(
                "Product created specifically for transaction rollback testing"
        );

        product.setPrice(100.0);
        product.setStock(10);

        product = productRepository.saveAndFlush(product);

        initialOrderCount = orderRepository.count();

        // SET AUTHENTICATED USER
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email,
                        "test-password",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER")
                        )
                )
        );

        // MOCK REDIS RATE LIMITING
        when(rateLimitService.checkRateLimit(
                anyString(),
                anyInt(),
                any(Duration.class)
        )).thenReturn(
                new RateLimitResult(
                        true,
                        100,
                        1L,
                        99L,
                        0
                )
        );

        // SIMULATE OUTBOX DATABASE FAILURE
        doThrow(new DataIntegrityViolationException(
                "Simulated outbox database failure"
        )).when(outboxEventRepository).save(any());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRollbackOrderAndStockWhenOutboxSaveFails() {

        // CREATE ORDER ITEM
        OrderItemRequestDTO item = new OrderItemRequestDTO();

        item.setProductId(product.getId());
        item.setQuantity(2);

        // CREATE CHECKOUT REQUEST WITH SAVED ADDRESS
        OrderRequestDTO request = new OrderRequestDTO();

        request.setItems(List.of(item));
        request.setAddressId(deliveryAddress.getId());

        // EXPECT OUTBOX FAILURE
        assertThrows(
                DataIntegrityViolationException.class,
                () -> orderService.createOrder(
                        user.getId(),
                        request
                )
        );

        // VERIFY ORDER ROLLBACK
        assertEquals(
                initialOrderCount,
                orderRepository.count(),
                "Order must roll back when outbox persistence fails"
        );

        // VERIFY INVENTORY ROLLBACK
        Product reloadedProduct = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertEquals(
                10,
                reloadedProduct.getStock(),
                "Product stock must remain unchanged after rollback"
        );

        // VERIFY OUTBOX SAVE WAS ATTEMPTED
        verify(outboxEventRepository).save(any());
    }
}
