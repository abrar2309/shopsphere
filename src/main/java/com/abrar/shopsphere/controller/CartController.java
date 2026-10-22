package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.AddCartItemRequestDTO;
import com.abrar.shopsphere.dto.CartResponseDTO;
import com.abrar.shopsphere.dto.UpdateCartItemRequestDTO;
import com.abrar.shopsphere.service.CartService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(
            CartService cartService) {

        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponseDTO> getCart() {

        return ResponseEntity.ok(
                cartService.getCart()
        );
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponseDTO> addItem(
            @Valid
            @RequestBody
            AddCartItemRequestDTO request) {

        return ResponseEntity.ok(
                cartService.addItem(request)
        );
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponseDTO> updateItem(
            @PathVariable Long productId,
            @Valid
            @RequestBody
            UpdateCartItemRequestDTO request) {

        return ResponseEntity.ok(
                cartService.updateItem(
                        productId,
                        request
                )
        );
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponseDTO> removeItem(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                cartService.removeItem(productId)
        );
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {

        cartService.clearCart();

        return ResponseEntity.noContent()
                .build();
    }
}