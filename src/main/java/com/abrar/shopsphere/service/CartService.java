package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.AddCartItemRequestDTO;
import com.abrar.shopsphere.dto.CartItemResponseDTO;
import com.abrar.shopsphere.dto.CartResponseDTO;
import com.abrar.shopsphere.dto.UpdateCartItemRequestDTO;
import com.abrar.shopsphere.entity.Cart;
import com.abrar.shopsphere.entity.CartItem;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.exception.CartResourceNotFoundException;
import com.abrar.shopsphere.repository.CartItemRepository;
import com.abrar.shopsphere.repository.CartRepository;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CartResponseDTO getCart() {
        User user = getCurrentUser();
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));
        return mapToResponse(cart);
    }

    @Transactional
    public CartResponseDTO addItem(AddCartItemRequestDTO request) {
        User user = getCurrentUser();

        if (request == null || request.getProductId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A valid product ID is required.");
        }
        validateQuantity(request.getQuantity());

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));

        // Lock protects the stock read while the cart request is processed.
        // A cart does not reserve inventory; OrderService rechecks and deducts stock.
        Product product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new CartResourceNotFoundException("Product not found"));

        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);

        int requestedTotal = request.getQuantity();
        if (cartItem != null) {
            requestedTotal += cartItem.getQuantity();
        }
        validateAvailableStock(product, requestedTotal);

        if (cartItem == null) {
            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setQuantity(requestedTotal);
            cart.addItem(cartItem);
        } else {
            cartItem.setQuantity(requestedTotal);
        }

        cartItemRepository.save(cartItem);
        return mapToResponse(cart);
    }

    @Transactional
    public CartResponseDTO updateItem(Long productId, UpdateCartItemRequestDTO request) {
        User user = getCurrentUser();

        if (productId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A valid product ID is required.");
        }

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new CartResourceNotFoundException("Cart not found"));

        // Check ownership/existence before request validation. This preserves the
        // not-found contract and prevents probing another customer's cart contents.
        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new CartResourceNotFoundException("Product not found in cart"));

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A valid quantity is required.");
        }
        validateQuantity(request.getQuantity());

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new CartResourceNotFoundException("Product not found"));

        validateAvailableStock(product, request.getQuantity());
        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);
        return mapToResponse(cart);
    }

    @Transactional
    public CartResponseDTO removeItem(Long productId) {
        User user = getCurrentUser();
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new CartResourceNotFoundException("Cart not found"));

        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new CartResourceNotFoundException("Product not found in cart"));

        cart.removeItem(cartItem);
        cartItemRepository.delete(cartItem);
        return mapToResponse(cart);
    }

    @Transactional
    public void clearCart() {
        User user = getCurrentUser();
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new CartResourceNotFoundException("Cart not found"));
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Quantity must be at least 1.");
        }
    }

    private void validateAvailableStock(Product product, int requestedQuantity) {
        Integer stock = product.getStock();
        if (stock == null || stock <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This product is currently out of stock.");
        }
        if (requestedQuantity > stock) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only " + stock + " item(s) are currently available.");
        }
    }

    private Cart createCart(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.save(cart);
    }

    private User getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("User is not authenticated");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found"));
    }

    private CartResponseDTO mapToResponse(Cart cart) {
        List<CartItemResponseDTO> items = cart.getItems().stream()
                .map(this::mapItem)
                .toList();

        BigDecimal totalAmount = items.stream()
                .map(CartItemResponseDTO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponseDTO(cart.getId(), items, totalAmount);
    }

    private CartItemResponseDTO mapItem(CartItem item) {
        BigDecimal price = BigDecimal.valueOf(item.getProduct().getPrice());
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));

        return new CartItemResponseDTO(
                item.getProduct().getId(),
                item.getProduct().getName(),
                price,
                item.getQuantity(),
                subtotal);
    }
}
