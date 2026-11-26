
package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.WishlistItemDTO;
import com.abrar.shopsphere.service.WishlistService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wishlist")
@PreAuthorize("isAuthenticated()")
public class WishlistController {

    private final WishlistService wishlist;

    public WishlistController(WishlistService wishlist) {
        this.wishlist = wishlist;
    }

    @GetMapping
    public ResponseEntity<List<WishlistItemDTO>> getWishlist(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                wishlist.getWishlist(authentication.getName())
        );
    }

    @PostMapping("/items")
    public ResponseEntity<Map<String, String>> add(
            Authentication authentication,
            @RequestBody Map<String, Long> request
    ) {
        Long productId = request.get("productId");

        wishlist.add(authentication.getName(), productId);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of("message", "Product added to wishlist.")
        );
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Map<String, String>> remove(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        wishlist.remove(authentication.getName(), productId);

        return ResponseEntity.ok(
                Map.of("message", "Product removed from wishlist.")
        );
    }

    @GetMapping("/{productId}/contains")
    public ResponseEntity<Map<String, Boolean>> contains(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        return ResponseEntity.ok(
                Map.of(
                        "saved",
                        wishlist.contains(
                                authentication.getName(),
                                productId
                        )
                )
        );
    }
}
