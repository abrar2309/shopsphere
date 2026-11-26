
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.OrderItemRequestDTO;
import com.abrar.shopsphere.dto.OrderRequestDTO;
import com.abrar.shopsphere.dto.RateLimitResult;

import com.abrar.shopsphere.entity.Address;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;

import com.abrar.shopsphere.exception.InsufficientStockException;

import com.abrar.shopsphere.repository.AddressRepository;
import com.abrar.shopsphere.repository.OrderRepository;
import com.abrar.shopsphere.repository.OutboxEventRepository;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class OrderServiceConcurrencyTest {

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

    @Test
    void concurrentOrdersMustNotOversellInventory() throws Exception {

        // 1. Create two independent customers.
        User customerA = createUser("customer-a");
        User customerB = createUser("customer-b");

        // 2. Create a delivery address for each customer.
        Address addressA = createAddress(customerA);
        Address addressB = createAddress(customerB);

        // 3. Create a product with only five units available.
        Product product = new Product();

        product.setName("Concurrency Test Product");
        product.setDescription("Inventory concurrency test");
        product.setPrice(100.0);
        product.setStock(5);

        product = productRepository.saveAndFlush(product);

        Long productId = product.getId();

        long initialOrderCount = orderRepository.count();

        // 4. Allow mocked rate limiting for both customers.
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

        // 5. Let the mocked outbox repository accept events.
        when(outboxEventRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 6. Synchronize both requests so they start together.
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {

            Future<Boolean> customerAResult = executor.submit(() -> {

                ready.countDown();
                start.await();

                return attemptOrder(
                        customerA.getEmail(),
                        addressA.getId(),
                        productId
                );
            });

            Future<Boolean> customerBResult = executor.submit(() -> {

                ready.countDown();
                start.await();

                return attemptOrder(
                        customerB.getEmail(),
                        addressB.getId(),
                        productId
                );
            });

            // Both workers must be ready before releasing them.
            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "Both customer requests should become ready"
            );

            start.countDown();

            // true = order succeeded; false = insufficient stock.
            boolean resultA = customerAResult.get(
                    15,
                    TimeUnit.SECONDS
            );

            boolean resultB = customerBResult.get(
                    15,
                    TimeUnit.SECONDS
            );

            // 7. Exactly one order should succeed.
            assertTrue(
                    resultA ^ resultB,
                    "Exactly one customer should successfully place an order"
            );

            // 8. Verify final stock.
            Product updatedProduct = productRepository
                    .findById(productId)
                    .orElseThrow();

            assertEquals(
                    1,
                    updatedProduct.getStock(),
                    "Inventory must never become negative or oversell"
            );

            // 9. Exactly one new order should exist.
            assertEquals(
                    initialOrderCount + 1,
                    orderRepository.count(),
                    "Only one order should be persisted"
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean attemptOrder(
            String email,
            Long addressId,
            Long productId
    ) {

        try {

            // SecurityContext is thread-local.
            // Each worker must authenticate independently.
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            "test-password",
                            List.of(
                                    new SimpleGrantedAuthority("ROLE_USER")
                            )
                    )
            );

            OrderItemRequestDTO item = new OrderItemRequestDTO();

            item.setProductId(productId);
            item.setQuantity(4);

            OrderRequestDTO request = new OrderRequestDTO();

            request.setAddressId(addressId);
            request.setItems(List.of(item));

            orderService.createOrderForAuthenticatedUser(request);

            return true;

        } catch (InsufficientStockException exception) {

            return false;

        } finally {

            SecurityContextHolder.clearContext();
        }
    }

    private User createUser(String prefix) {

        String email = prefix + "-"
                + UUID.randomUUID()
                + "@shopsphere.test";

        User user = new User();

        user.setFirstName("Test");
        user.setLastName("Customer");
        user.setEmail(email);
        user.setPhoneNumber("9000000000");
        user.setPassword("test-password");
        user.setRole("ROLE_USER");

        return userRepository.saveAndFlush(user);
    }

    private Address createAddress(User user) {

        Address address = new Address();

        address.setUser(user);
        address.setLabel("Home");
        address.setRecipientName("Test Customer");
        address.setPhoneNumber("9000000000");
        address.setAddressLine1("123 Test Street");
        address.setAddressLine2("Near Test Park");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setPostalCode("560001");
        address.setCountry("India");
        address.setDefault(true);

        return addressRepository.saveAndFlush(address);
    }
}
