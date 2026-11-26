
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.OrderRequestDTO;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.AddressRepository;
import com.abrar.shopsphere.repository.OrderRepository;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceSecurityTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private com.abrar.shopsphere.config.RateLimitProperties rateLimitProperties;

    @InjectMocks
    private OrderService orderService;

    private User customerA;
    private User customerB;

    @BeforeEach
    void setUp() {

        customerA = new User();
        customerA.setId(1L);
        customerA.setEmail("customerA@test.com");
        customerA.setRole("ROLE_USER");

        customerB = new User();
        customerB.setId(2L);
        customerB.setEmail("customerB@test.com");
        customerB.setRole("ROLE_USER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    @Test
    void customerCannotCreateOrderForAnotherCustomer() {

        authenticate("customerA@test.com");

        when(userRepository.findByEmail("customerA@test.com"))
                .thenReturn(Optional.of(customerA));

        assertThrows(
                AccessDeniedException.class,
                () -> orderService.createOrder(2L, null)
        );

        verify(userRepository, times(1))
                .findByEmail("customerA@test.com");

        verifyNoInteractions(
                orderRepository,
                productRepository,
                addressRepository,
                rateLimitService,
                outboxService
        );
    }

    @Test
    void customerCannotUseAnotherCustomersDeliveryAddress() {

        authenticate("customerA@test.com");

        when(userRepository.findByEmail("customerA@test.com"))
                .thenReturn(Optional.of(customerA));

        // Address 200 belongs to another customer or does not belong
        // to the currently authenticated customer.
        when(addressRepository.findByIdAndUserId(200L, 1L))
                .thenReturn(Optional.empty());

        OrderRequestDTO request = new OrderRequestDTO();
        request.setAddressId(200L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(1L, request)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(addressRepository)
                .findByIdAndUserId(200L, 1L);

        // Checkout must stop before inventory or order processing.
        verifyNoInteractions(
                orderRepository,
                productRepository,
                rateLimitService,
                outboxService
        );
    }

    @Test
    void customerOrderHistoryUsesAuthenticatedUserId() {

        authenticate("customerA@test.com");

        when(userRepository.findByEmail("customerA@test.com"))
                .thenReturn(Optional.of(customerA));

        when(orderRepository.findOrdersWithItemsByUserId(1L))
                .thenReturn(List.of());

        var result = orderService.getMyOrders();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(orderRepository)
                .findOrdersWithItemsByUserId(1L);

        verify(orderRepository, never())
                .findOrdersWithItemsByUserId(2L);

        verifyNoInteractions(addressRepository);
    }

    @Test
    void unauthenticatedUserCannotCreateOrder() {

        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> orderService.createOrder(1L, null)
        );

        verifyNoInteractions(
                userRepository,
                orderRepository,
                productRepository,
                addressRepository,
                rateLimitService,
                outboxService
        );
    }

    @Test
    void unauthenticatedUserCannotRetrieveOrderHistory() {

        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> orderService.getMyOrders()
        );

        verifyNoInteractions(
                userRepository,
                orderRepository
        );
    }
}
