
package com.abrar.shopsphere.security;

import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
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
class ProductSecurityTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductSecurity productSecurity;

    private User sellerA;
    private User sellerB;
    private Product product;

    @BeforeEach
    void setUp() {

        sellerA = new User();
        sellerA.setId(1L);
        sellerA.setEmail("sellerA@test.com");
        sellerA.setRole("SELLER");

        sellerB = new User();
        sellerB.setId(2L);
        sellerB.setEmail("sellerB@test.com");
        sellerB.setRole("SELLER");

        product = new Product();
        product.setId(100L);
        product.setName("Test Laptop");
        product.setSeller(sellerA);
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
    void sellerCanAccessOwnProduct() {

        authenticate("sellerA@test.com");

        when(userRepository.findByEmail("sellerA@test.com"))
                .thenReturn(Optional.of(sellerA));

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        assertTrue(productSecurity.isOwner(100L));
    }

    @Test
    void sellerCannotAccessAnotherSellersProduct() {

        authenticate("sellerB@test.com");

        when(userRepository.findByEmail("sellerB@test.com"))
                .thenReturn(Optional.of(sellerB));

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        assertFalse(productSecurity.isOwner(100L));
    }

    @Test
    void adminCanAccessAnyProduct() {

        User admin = new User();
        admin.setId(3L);
        admin.setEmail("admin@test.com");
        admin.setRole("ADMIN");

        authenticate("admin@test.com");

        when(userRepository.findByEmail("admin@test.com"))
                .thenReturn(Optional.of(admin));

        assertTrue(productSecurity.isOwner(100L));

        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    void accessDeniedWhenProductDoesNotExist() {

        authenticate("sellerA@test.com");

        when(userRepository.findByEmail("sellerA@test.com"))
                .thenReturn(Optional.of(sellerA));

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertFalse(productSecurity.isOwner(999L));
    }

    @Test
    void accessDeniedWhenProductHasNoSeller() {

        authenticate("sellerA@test.com");

        product.setSeller(null);

        when(userRepository.findByEmail("sellerA@test.com"))
                .thenReturn(Optional.of(sellerA));

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        assertFalse(productSecurity.isOwner(100L));
    }

    @Test
    void accessDeniedWhenAuthenticatedUserDoesNotExist() {

        authenticate("unknown@test.com");

        when(userRepository.findByEmail("unknown@test.com"))
                .thenReturn(Optional.empty());

        assertFalse(productSecurity.isOwner(100L));

        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    void accessDeniedWhenNoUserIsAuthenticated() {

        assertFalse(productSecurity.isOwner(100L));

        verifyNoInteractions(userRepository, productRepository);
    }
}
