
        package com.abrar.shopsphere.service;

import com.abrar.shopsphere.config.RateLimitProperties;

import com.abrar.shopsphere.dto.OrderItemRequestDTO;
import com.abrar.shopsphere.dto.OrderItemResponseDTO;
import com.abrar.shopsphere.dto.OrderRequestDTO;
import com.abrar.shopsphere.dto.OrderResponseDTO;

import com.abrar.shopsphere.entity.Order;
import com.abrar.shopsphere.entity.OrderItem;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;

import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.event.OrderItemEvent;

import com.abrar.shopsphere.exception.InsufficientStockException;

import com.abrar.shopsphere.repository.OrderRepository;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.slf4j.MDC;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

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
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public OrderService(
            OrderRepository orderRepository,
            OutboxService outboxService,
            ProductRepository productRepository,
            UserRepository userRepository,
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties) {

        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * Preferred checkout endpoint service method.
     * The user ID is obtained from the authenticated JWT principal,
     * never from the browser request.
     */
    @Transactional
    public OrderResponseDTO createOrderForAuthenticatedUser(
            OrderRequestDTO requestDTO) {

        User user = getAuthenticatedUser();

        return createOrder(user.getId(), requestDTO);
    }

    /**
     * Creates an order for a specific user.
     * Ownership is verified before any inventory is modified.
     */
    @Transactional
    public OrderResponseDTO createOrder(
            Long userId,
            OrderRequestDTO requestDTO) {

        User authenticatedUser = getAuthenticatedUser();

        if (!authenticatedUser.getId().equals(userId)) {
            throw new AccessDeniedException(
                    "You are not allowed to create an order for another user."
            );
        }

        // Rate limiting: per authenticated user.
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

        // Create the order.
        Order order = new Order();
        order.setUser(authenticatedUser);
        order.setStatus("CREATED");

        double totalAmount = 0.0;

        List<OrderItemResponseDTO> responseItems =
                new ArrayList<>();

        // Process each requested product while holding a database row lock.
        for (OrderItemRequestDTO itemDTO : requestDTO.getItems()) {

            Product product = productRepository
                    .findByIdForUpdate(itemDTO.getProductId())
                    .orElseThrow(() ->
                            new RuntimeException("Product not found")
                    );

            int requestedQuantity = itemDTO.getQuantity();

            if (product.getStock() < requestedQuantity) {
                throw new InsufficientStockException(
                        "Insufficient stock for product: "
                                + product.getName()
                );
            }

            // Decrease stock within the current transaction.
            product.setStock(
                    product.getStock() - requestedQuantity
            );

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

        // Persist the order.
        Order savedOrder = orderRepository.save(order);

        // Build the event items for the transactional outbox.
        List<OrderItemEvent> eventItems = responseItems
                .stream()
                .map(item -> new OrderItemEvent(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .toList();

        String correlationId = MDC.get("requestId");

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

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

        // Preserve the existing outbox integration.
        outboxService.saveOrderCreatedEvent(orderCreatedEvent);

        return new OrderResponseDTO(
                savedOrder.getId(),
                savedOrder.getTotalAmount(),
                savedOrder.getStatus(),
                responseItems
        );
    }

    /**
     * Returns the authenticated user's order history only.
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
     * Resolves the logged-in user using the email in the security principal.
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

        return new OrderResponseDTO(
                order.getId(),
                order.getTotalAmount(),
                order.getStatus(),
                items
        );
    }
}
