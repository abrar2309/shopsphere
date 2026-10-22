package com.abrar.shopsphere.service;

import com.abrar.shopsphere.config.RateLimitProperties;
import com.abrar.shopsphere.dto.LoginRequestDTO;
import com.abrar.shopsphere.dto.LoginResponseDTO;
import com.abrar.shopsphere.dto.LoginResult;
import com.abrar.shopsphere.dto.RateLimitResult;
import com.abrar.shopsphere.dto.UserRequestDTO;
import com.abrar.shopsphere.dto.UserResponseDTO;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final HttpServletRequest request;
    private final RateLimitProperties rateLimitProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RateLimitService rateLimitService,
            HttpServletRequest request,
            RateLimitProperties rateLimitProperties) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
        this.request = request;
        this.rateLimitProperties = rateLimitProperties;
    }

    public UserResponseDTO register(
            UserRequestDTO dto) {

        if (userRepository
                .findByEmail(dto.getEmail())
                .isPresent()) {

            throw new RuntimeException(
                    "Email already registered."
            );
        }

        User user = new User();

        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhoneNumber(dto.getPhoneNumber());

        user.setPassword(
                passwordEncoder.encode(
                        dto.getPassword()
                )
        );

        user.setRole("CUSTOMER");

        User savedUser =
                userRepository.save(user);

        UserResponseDTO response =
                new UserResponseDTO();

        response.setId(savedUser.getId());
        response.setFirstName(savedUser.getFirstName());
        response.setLastName(savedUser.getLastName());
        response.setEmail(savedUser.getEmail());
        response.setPhoneNumber(savedUser.getPhoneNumber());

        return response;
    }

    public LoginResult login(
            LoginRequestDTO dto) {

        /*
         * Identify client by IP.
         */
        String ipAddress =
                request.getRemoteAddr();

        String key =
                "rate_limit:login:" + ipAddress;

        /*
         * Read configuration.
         */
        int limit =
                rateLimitProperties
                        .getLogin()
                        .getLimit();

        long windowSeconds =
                rateLimitProperties
                        .getLogin()
                        .getWindowSeconds();

        /*
         * Apply rate limit.
         */
        RateLimitResult rateLimitResult =
                rateLimitService.checkRateLimit(
                        key,
                        limit,
                        Duration.ofSeconds(
                                windowSeconds
                        )
                );

        /*
         * Authenticate.
         */
        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                dto.getEmail(),
                                dto.getPassword()
                        )
                );

        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();

        User user =
                userRepository
                        .findByEmail(dto.getEmail())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found."
                                )
                        );

        /*
         * Generate JWT.
         */
        String token =
                jwtService.generateToken(
                        userDetails.getUsername(),
                        user.getRole()
                );

        LoginResponseDTO loginResponse =
                new LoginResponseDTO(
                        token,
                        user.getEmail(),
                        user.getRole()
                );

        return new LoginResult(
                loginResponse,
                rateLimitResult
        );
    }
}