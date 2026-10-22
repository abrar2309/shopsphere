package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class CustomUserDetailsService
        implements UserDetailsService {


    private final UserRepository userRepository;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public CustomUserDetailsService(
            UserRepository userRepository) {

        this.userRepository = userRepository;
    }


    // ============================================================
    // LOAD USER
    // ============================================================

    @Override
    public UserDetails loadUserByUsername(
            String email)
            throws UsernameNotFoundException {


        // --------------------------------------------------------
        // 1. FIND USER
        // --------------------------------------------------------

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new UsernameNotFoundException(
                                        "User not found with email: "
                                                + email
                                )
                        );


        // --------------------------------------------------------
        // 2. CONVERT ROLE TO SPRING SECURITY AUTHORITY
        // --------------------------------------------------------

        String authority =
                "ROLE_"
                        + user.getRole()
                        .toUpperCase(Locale.ROOT);


        // --------------------------------------------------------
        // 3. CREATE SPRING SECURITY USER
        // --------------------------------------------------------

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),

                List.of(
                        new SimpleGrantedAuthority(
                                authority
                        )
                )
        );
    }
}