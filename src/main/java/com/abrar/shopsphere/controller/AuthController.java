package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.LoginRequestDTO;
import com.abrar.shopsphere.dto.LoginResponseDTO;
import com.abrar.shopsphere.dto.LoginResult;
import com.abrar.shopsphere.dto.UserRequestDTO;
import com.abrar.shopsphere.dto.UserResponseDTO;
import com.abrar.shopsphere.service.AuthService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(
            AuthService authService) {

        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(
            @RequestBody UserRequestDTO dto) {

        UserResponseDTO response =
                authService.register(dto);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @RequestBody LoginRequestDTO dto) {

        LoginResult result =
                authService.login(dto);

        HttpHeaders headers =
                new HttpHeaders();

        headers.set(
                "X-RateLimit-Limit",
                String.valueOf(
                        result
                                .getRateLimitResult()
                                .getLimit()
                )
        );

        headers.set(
                "X-RateLimit-Remaining",
                String.valueOf(
                        result
                                .getRateLimitResult()
                                .getRemaining()
                )
        );

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(
                        result.getLoginResponse()
                );
    }
}