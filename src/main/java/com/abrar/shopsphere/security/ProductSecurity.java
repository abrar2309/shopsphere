package com.abrar.shopsphere.security;

import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("productSecurity")
public class ProductSecurity {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public ProductSecurity(
            ProductRepository productRepository,
            UserRepository userRepository) {

        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }


    // ============================================================
    // CHECK PRODUCT OWNERSHIP
    // ============================================================

    public boolean isOwner(Long productId) {

        // --------------------------------------------------------
        // GET CURRENT AUTHENTICATION
        // --------------------------------------------------------

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();


        if (authentication == null
                || !authentication.isAuthenticated()) {

            return false;
        }


        // --------------------------------------------------------
        // GET CURRENT USER
        // --------------------------------------------------------

        String email =
                authentication.getName();


        User currentUser =
                userRepository
                        .findByEmail(email)
                        .orElse(null);


        if (currentUser == null) {

            return false;
        }


        // --------------------------------------------------------
        // ADMIN CAN ACCESS ANY PRODUCT
        // --------------------------------------------------------

        if ("ADMIN".equalsIgnoreCase(
                currentUser.getRole())) {

            return true;
        }


        // --------------------------------------------------------
        // LOAD PRODUCT
        // --------------------------------------------------------

        Product product =
                productRepository
                        .findById(productId)
                        .orElse(null);


        if (product == null) {

            return false;
        }


        // --------------------------------------------------------
        // PRODUCT MUST HAVE OWNER
        // --------------------------------------------------------

        if (product.getSeller() == null) {

            return false;
        }


        // --------------------------------------------------------
        // COMPARE OWNER IDs
        // --------------------------------------------------------

        return product
                .getSeller()
                .getId()
                .equals(currentUser.getId());
    }
}