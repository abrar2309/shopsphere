
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

    // Preferred endpoint: user identity comes from authentication.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponseDTO createMyOrder(
            @Valid @RequestBody OrderRequestDTO requestDTO) {

        return orderService.createOrderForAuthenticatedUser(
                requestDTO
        );
    }

    // Returns orders belonging to the authenticated user.
    @GetMapping
    public List<OrderResponseDTO> getMyOrders() {
        return orderService.getMyOrders();
    }

    // Backward-compatible endpoint with server-side ownership checks.
    @PostMapping("/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponseDTO createOrder(
            @PathVariable Long userId,
            @Valid @RequestBody OrderRequestDTO requestDTO) {

        return orderService.createOrder(userId, requestDTO);
    }
}
