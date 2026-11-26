
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.OrderItemRequestDTO;
import com.abrar.shopsphere.dto.OrderRequestDTO;
import com.abrar.shopsphere.dto.RateLimitResult;
import org.springframework.test.annotation.DirtiesContext;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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

@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "app.kafka.enabled=false",
        "shopsphere.outbox.enabled=false",
        "spring.task.scheduling.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class OrderServicePostgresConcurrencyTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("shopsphere_test")
                    .withUsername("shopsphere")
                    .withPassword("shopsphere_test");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {

        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );

        registry.add(
                "spring.datasource.driver-class-name",
                POSTGRES::getDriverClassName
        );
    }

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

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean(
            name = "rateLimitService",
            enforceOverride = true
    )
    private RateLimitService rateLimitService;

    @Test
    void concurrentOrdersMustNotOversellInventoryInPostgres()
            throws Exception {

        // 1. Create two independent customers.
        User customerA = createUser("postgres-customer-a");
        User customerB = createUser("postgres-customer-b");

        // 2. Create separate delivery addresses.
        Address addressA = createAddress(customerA);
        Address addressB = createAddress(customerB);

        // 3. Create a product with five units.
        Product product = new Product();

        product.setName("PostgreSQL Concurrency Product");
        product.setDescription("PostgreSQL row-locking integration test");
        product.setPrice(100.0);
        product.setStock(5);

        product = productRepository.saveAndFlush(product);

        Long productId = product.getId();

        long initialOrderCount = orderRepository.count();
        long initialOutboxCount = outboxEventRepository.count();

        // 4. Mock only Redis rate limiting.
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

        // 5. Start two independent transactions concurrently.
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            Future<Boolean> customerAResult =
                    executor.submit(() -> {

                        ready.countDown();
                        start.await();

                        return attemptOrder(
                                customerA.getEmail(),
                                addressA.getId(),
                                productId
                        );
                    });

            Future<Boolean> customerBResult =
                    executor.submit(() -> {

                        ready.countDown();
                        start.await();

                        return attemptOrder(
                                customerB.getEmail(),
                                addressB.getId(),
                                productId
                        );
                    });

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both customer transactions should be ready"
            );

            // Release both requests together.
            start.countDown();

            boolean resultA = customerAResult.get(
                    30,
                    TimeUnit.SECONDS
            );

            boolean resultB = customerBResult.get(
                    30,
                    TimeUnit.SECONDS
            );

            // 6. Exactly one order must succeed.
            assertTrue(
                    resultA ^ resultB,
                    "Exactly one customer must successfully place an order"
            );

            // 7. Verify inventory in PostgreSQL.
            Product updatedProduct = productRepository
                    .findById(productId)
                    .orElseThrow();

            assertEquals(
                    1,
                    updatedProduct.getStock(),
                    "Final PostgreSQL inventory must be one"
            );

            // 8. Verify exactly one order was committed.
            assertEquals(
                    initialOrderCount + 1,
                    orderRepository.count(),
                    "Exactly one order must be committed"
            );

            // 9. Verify exactly one outbox event was committed.
            assertEquals(
                    initialOutboxCount + 1,
                    outboxEventRepository.count(),
                    "Exactly one outbox event must be committed"
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
        address.setAddressLine1("123 PostgreSQL Test Street");
        address.setAddressLine2("Near Test Park");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setPostalCode("560001");
        address.setCountry("India");
        address.setDefault(true);

        return addressRepository.saveAndFlush(address);
    }
}
