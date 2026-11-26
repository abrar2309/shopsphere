
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.config.RateLimitProperties;
import com.abrar.shopsphere.dto.OrderItemRequestDTO;
import com.abrar.shopsphere.dto.OrderRequestDTO;
import com.abrar.shopsphere.dto.OrderResponseDTO;
import com.abrar.shopsphere.entity.Address;
import com.abrar.shopsphere.entity.Order;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.repository.AddressRepository;
import com.abrar.shopsphere.repository.OrderRepository;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceCheckoutTest {

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
    private RateLimitProperties rateLimitProperties;

    @Mock
    private RateLimitProperties.Policy orderCreatePolicy;

    @InjectMocks
    private OrderService orderService;

    private User customer;
    private Product product;
    private Address deliveryAddress;

    @BeforeEach
    void setUp() {

        customer = new User();
        customer.setId(1L);
        customer.setEmail("customer@test.com");
        customer.setRole("ROLE_USER");

        product = new Product(
                10L,
                "Test Product",
                "Product for checkout testing",
                500.0,
                10,
                null
        );

        deliveryAddress = new Address();
        deliveryAddress.setUser(customer);
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

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "customer@test.com",
                        "password",
                        List.of()
                )
        );

        when(userRepository.findByEmail("customer@test.com"))
                .thenReturn(Optional.of(customer));

        when(addressRepository.findByIdAndUserId(101L, 1L))
                .thenReturn(Optional.of(deliveryAddress));

        when(rateLimitProperties.getOrderCreate())
                .thenReturn(orderCreatePolicy);

        when(orderCreatePolicy.getLimit()).thenReturn(10);
        when(orderCreatePolicy.getWindowSeconds()).thenReturn(60L);

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(product));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedCustomerCanSuccessfullyCheckout() {

        // CREATE ORDER ITEM
        OrderItemRequestDTO item = new OrderItemRequestDTO();
        item.setProductId(10L);
        item.setQuantity(2);

        // CREATE CHECKOUT REQUEST WITH DELIVERY ADDRESS
        OrderRequestDTO request = new OrderRequestDTO();
        request.setItems(List.of(item));
        request.setAddressId(101L);

        // EXECUTE CHECKOUT
        OrderResponseDTO response =
                orderService.createOrderForAuthenticatedUser(request);

        // VERIFY CHECKOUT RESPONSE
        assertNotNull(response);
        assertEquals(1000.0, response.getTotalAmount());
        assertEquals("CREATED", response.getStatus());

        // VERIFY DELIVERY ADDRESS IN RESPONSE
        assertNotNull(response.getDeliveryAddress());
        assertEquals(
                "Test Customer",
                response.getDeliveryAddress().getRecipientName()
        );
        assertEquals(
                "123 Test Street",
                response.getDeliveryAddress().getAddressLine1()
        );
        assertEquals(
                "Bengaluru",
                response.getDeliveryAddress().getCity()
        );
        assertEquals(
                "Karnataka",
                response.getDeliveryAddress().getState()
        );

        // VERIFY INVENTORY DEDUCTION
        assertEquals(8, product.getStock());

        // CAPTURE SAVED ORDER
        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        // VERIFY ORDER OWNERSHIP AND BUSINESS DETAILS
        assertEquals(customer.getId(), savedOrder.getUser().getId());
        assertEquals(1000.0, savedOrder.getTotalAmount());
        assertEquals("CREATED", savedOrder.getStatus());

        assertEquals(1, savedOrder.getItems().size());
        assertEquals(2, savedOrder.getItems().get(0).getQuantity());

        // VERIFY DELIVERY ADDRESS SNAPSHOT
        assertNotNull(savedOrder.getDeliveryAddress());
        assertEquals(
                "Test Customer",
                savedOrder.getDeliveryAddress().getRecipientName()
        );
        assertEquals(
                "123 Test Street",
                savedOrder.getDeliveryAddress().getAddressLine1()
        );
        assertEquals(
                "Bengaluru",
                savedOrder.getDeliveryAddress().getCity()
        );

        // VERIFY ADDRESS OWNERSHIP LOOKUP
        verify(addressRepository).findByIdAndUserId(101L, 1L);

        // VERIFY TRANSACTIONAL OUTBOX EVENT
        verify(outboxService).saveOrderCreatedEvent(
                any(OrderCreatedEvent.class)
        );

        // VERIFY PRODUCT ROW LOCK
        verify(productRepository).findByIdForUpdate(10L);

        // VERIFY RATE LIMITING
        verify(rateLimitService).checkRateLimit(
                eq("rate_limit:order-create:1"),
                eq(10),
                any()
        );
    }
}
