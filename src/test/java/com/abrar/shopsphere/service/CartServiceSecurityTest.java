
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.Cart;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.exception.CartResourceNotFoundException;
import com.abrar.shopsphere.repository.CartItemRepository;
import com.abrar.shopsphere.repository.CartRepository;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceSecurityTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartService cartService;

    private User customerA;
    private User customerB;
    private Cart cartA;

    @BeforeEach
    void setUp() {

        customerA = new User();
        customerA.setId(1L);
        customerA.setEmail("customerA@test.com");
        customerA.setRole("CUSTOMER");

        customerB = new User();
        customerB.setId(2L);
        customerB.setEmail("customerB@test.com");
        customerB.setRole("CUSTOMER");

        cartA = new Cart();
        cartA.setId(100L);
        cartA.setUser(customerA);
    }

    @AfterEach
    void clearSecurityContext() {
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
    void customerCanRetrieveOwnCart() {

        authenticate("customerA@test.com");

        when(userRepository.findByEmail("customerA@test.com"))
                .thenReturn(Optional.of(customerA));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cartA));

        var response = cartService.getCart();

        assertEquals(100L, response.getCartId());
        assertTrue(response.getItems().isEmpty());

        verify(cartRepository).findByUserId(1L);
    }

    @Test
    void customerCannotUpdateItemOutsideOwnCart() {

        authenticate("customerA@test.com");

        when(userRepository.findByEmail("customerA@test.com"))
                .thenReturn(Optional.of(customerA));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cartA));

        when(cartItemRepository.findByCartIdAndProductId(100L, 500L))
                .thenReturn(Optional.empty());

        assertThrows(
                CartResourceNotFoundException.class,
                () -> cartService.updateItem(500L, null)
        );

        verify(cartItemRepository)
                .findByCartIdAndProductId(100L, 500L);

        verify(cartItemRepository, never())
                .save(any());
    }

    @Test
    void customerCannotRemoveItemOutsideOwnCart() {

        authenticate("customerA@test.com");

        when(userRepository.findByEmail("customerA@test.com"))
                .thenReturn(Optional.of(customerA));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cartA));

        when(cartItemRepository.findByCartIdAndProductId(100L, 500L))
                .thenReturn(Optional.empty());

        assertThrows(
                CartResourceNotFoundException.class,
                () -> cartService.removeItem(500L)
        );

        verify(cartItemRepository)
                .findByCartIdAndProductId(100L, 500L);

        verify(cartItemRepository, never())
                .delete(any());
    }

    @Test
    void customerCannotAccessAnotherCustomersCart() {

        authenticate("customerB@test.com");

        when(userRepository.findByEmail("customerB@test.com"))
                .thenReturn(Optional.of(customerB));

        Cart cartB = new Cart();
        cartB.setId(200L);
        cartB.setUser(customerB);

        when(cartRepository.findByUserId(2L))
                .thenReturn(Optional.of(cartB));

        var response = cartService.getCart();

        assertEquals(200L, response.getCartId());
        assertNotEquals(100L, response.getCartId());

        verify(cartRepository).findByUserId(2L);
        verify(cartRepository, never()).findByUserId(1L);
    }

    @Test
    void unauthenticatedUserCannotAccessCart() {

        SecurityContextHolder.clearContext();

        assertThrows(
                IllegalStateException.class,
                () -> cartService.getCart()
        );

        verifyNoInteractions(
                cartRepository,
                cartItemRepository,
                productRepository,
                userRepository
        );
    }
}
