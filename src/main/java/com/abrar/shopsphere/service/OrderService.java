
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.config.RateLimitProperties;

import com.abrar.shopsphere.dto.OrderAddressResponseDTO;
import com.abrar.shopsphere.dto.OrderItemRequestDTO;
import com.abrar.shopsphere.dto.OrderItemResponseDTO;
import com.abrar.shopsphere.dto.OrderRequestDTO;
import com.abrar.shopsphere.dto.OrderResponseDTO;

import com.abrar.shopsphere.entity.Address;
import com.abrar.shopsphere.entity.Order;
import com.abrar.shopsphere.entity.OrderAddress;
import com.abrar.shopsphere.entity.OrderItem;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;

import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.event.OrderItemEvent;

import com.abrar.shopsphere.exception.InsufficientStockException;

import com.abrar.shopsphere.repository.AddressRepository;
import com.abrar.shopsphere.repository.OrderRepository;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.slf4j.MDC;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public OrderService(
            OrderRepository orderRepository,
            OutboxService outboxService,
            ProductRepository productRepository,
            UserRepository userRepository,
            AddressRepository addressRepository,
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties) {

        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * Creates an order for the authenticated customer.
     */
    @Transactional
    public OrderResponseDTO createOrderForAuthenticatedUser(
            OrderRequestDTO requestDTO) {

        User user = getAuthenticatedUser();

        return createOrder(user.getId(), requestDTO);
    }

    /**
     * Creates an order after verifying customer and address ownership.
     */
    @Transactional
    public OrderResponseDTO createOrder(
            Long userId,
            OrderRequestDTO requestDTO) {

        User authenticatedUser = getAuthenticatedUser();

        // Verify customer ownership.
        if (!authenticatedUser.getId().equals(userId)) {
            throw new AccessDeniedException(
                    "You are not allowed to create an order for another user."
            );
        }

        // Validate that the request exists.
        if (requestDTO == null) {
            throw new IllegalArgumentException(
                    "Order request is required."
            );
        }

        // Verify address ownership BEFORE validating order items.
        // This ensures unauthorized addresses return HTTP 404.
        Address address = addressRepository
                .findByIdAndUserId(
                        requestDTO.getAddressId(),
                        authenticatedUser.getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Delivery address not found."
                ));

        // Validate order items.
        if (requestDTO.getItems() == null
                || requestDTO.getItems().isEmpty()) {

            throw new IllegalArgumentException(
                    "Order must contain at least one item."
            );
        }

        // Create order.
        Order order = new Order();
        order.setUser(authenticatedUser);
        order.setStatus("CREATED");
        order.setDeliveryAddress(new OrderAddress(address));

        // Rate limiting: per authenticated customer.
        int limit = rateLimitProperties
                .getOrderCreate()
                .getLimit();

        long windowSeconds = rateLimitProperties
                .getOrderCreate()
                .getWindowSeconds();

        String rateLimitKey =
                "rate_limit:order-create:" + authenticatedUser.getId();

        rateLimitService.checkRateLimit(
                rateLimitKey,
                limit,
                Duration.ofSeconds(windowSeconds)
        );

        double totalAmount = 0.0;

        List<OrderItemResponseDTO> responseItems =
                new ArrayList<>();

        // Lock products while checking and updating inventory.
        for (OrderItemRequestDTO itemDTO : requestDTO.getItems()) {

            if (itemDTO == null || itemDTO.getQuantity() == null) {
                throw new IllegalArgumentException(
                        "Product quantity is required."
                );
            }

            int requestedQuantity = itemDTO.getQuantity();

            if (requestedQuantity <= 0) {
                throw new IllegalArgumentException(
                        "Product quantity must be greater than zero."
                );
            }

            Product product = productRepository
                    .findByIdForUpdate(itemDTO.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Product not found: " + itemDTO.getProductId()
                    ));

            Integer availableStock = product.getStock();

            if (availableStock == null) {
                throw new IllegalStateException(
                        "Inventory is not configured for product "
                                + product.getId()
                );
            }

            if (availableStock < requestedQuantity) {
                throw new InsufficientStockException(
                        "Insufficient stock for product "
                                + product.getName()
                                + ". Available: "
                                + availableStock
                                + ", requested: "
                                + requestedQuantity
                );
            }

            // Safe because stock was checked while holding the row lock.
            product.setStock(availableStock - requestedQuantity);

            double itemTotal =
                    product.getPrice() * requestedQuantity;

            totalAmount += itemTotal;

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(requestedQuantity);
            orderItem.setPrice(product.getPrice());

            order.addItem(orderItem);

            responseItems.add(
                    new OrderItemResponseDTO(
                            product.getId(),
                            requestedQuantity,
                            product.getPrice()
                    )
            );
        }

        order.setTotalAmount(totalAmount);

        // Persist order and order items.
        Order savedOrder = orderRepository.save(order);

        // Prepare order event items.
        List<OrderItemEvent> eventItems = responseItems
                .stream()
                .map(item -> new OrderItemEvent(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .toList();

        // Obtain correlation ID.
        String correlationId = MDC.get("requestId");

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Build order-created event.
        OrderCreatedEvent orderCreatedEvent =
                new OrderCreatedEvent(
                        UUID.randomUUID(),
                        correlationId,
                        savedOrder.getId(),
                        authenticatedUser.getId(),
                        savedOrder.getTotalAmount(),
                        savedOrder.getStatus(),
                        LocalDateTime.now(),
                        eventItems
                );

        // Order and outbox event remain in the same transaction.
        outboxService.saveOrderCreatedEvent(orderCreatedEvent);

        // Return order response.
        return new OrderResponseDTO(
                savedOrder.getId(),
                savedOrder.getTotalAmount(),
                savedOrder.getStatus(),
                responseItems,
                new OrderAddressResponseDTO(
                        savedOrder.getDeliveryAddress()
                )
        );
    }

    /**
     * Returns the authenticated customer's order history.
     */
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getMyOrders() {

        User user = getAuthenticatedUser();

        return orderRepository
                .findOrdersWithItemsByUserId(user.getId())
                .stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns one order belonging to the authenticated customer.
     */
    @Transactional(readOnly = true)
    public OrderResponseDTO getMyOrderById(Long orderId) {

        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid order ID."
            );
        }

        User user = getAuthenticatedUser();

        Order order = orderRepository
                .findOrderWithItemsByIdAndUserId(
                        orderId,
                        user.getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found."
                ));

        return toOrderResponse(order);
    }

    /**
     * Resolves the authenticated customer.
     */
    private User getAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new AccessDeniedException(
                    "Authentication is required."
            );
        }

        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found."
                        )
                );
    }

    /**
     * Converts an order entity into a response DTO.
     */
    private OrderResponseDTO toOrderResponse(Order order) {

        List<OrderItemResponseDTO> items =
                order.getItems()
                        .stream()
                        .map(item -> new OrderItemResponseDTO(
                                item.getProduct().getId(),
                                item.getQuantity(),
                                item.getPrice()
                        ))
                        .collect(Collectors.toList());

        OrderAddressResponseDTO deliveryAddress = null;

        if (order.getDeliveryAddress() != null) {
            deliveryAddress = new OrderAddressResponseDTO(
                    order.getDeliveryAddress()
            );
        }

        return new OrderResponseDTO(
                order.getId(),
                order.getTotalAmount(),
                order.getStatus(),
                items,
                deliveryAddress
        );
    }
}
