
package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.OrderRequestDTO;
import com.abrar.shopsphere.dto.OrderResponseDTO;
import com.abrar.shopsphere.service.OrderService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Create order for authenticated customer.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponseDTO createMyOrder(
            @Valid @RequestBody OrderRequestDTO requestDTO) {

        return orderService.createOrderForAuthenticatedUser(
                requestDTO
        );
    }

    // Get all orders belonging to authenticated customer.
    @GetMapping
    public List<OrderResponseDTO> getMyOrders() {

        return orderService.getMyOrders();
    }

    // Get one order belonging to authenticated customer.
    @GetMapping("/{orderId}")
    public OrderResponseDTO getMyOrder(
            @PathVariable Long orderId) {

        return orderService.getMyOrderById(orderId);
    }

    // Backward-compatible endpoint.
    // Ownership is verified inside OrderService.
    @PostMapping("/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponseDTO createOrder(
            @PathVariable Long userId,
            @Valid @RequestBody OrderRequestDTO requestDTO) {

        return orderService.createOrder(userId, requestDTO);
    }
}
