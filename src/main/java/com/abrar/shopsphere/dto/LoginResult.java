package com.abrar.shopsphere.dto;

public class LoginResult {

    private final LoginResponseDTO loginResponse;
    private final RateLimitResult rateLimitResult;

    public LoginResult(
            LoginResponseDTO loginResponse,
            RateLimitResult rateLimitResult) {

        this.loginResponse = loginResponse;
        this.rateLimitResult = rateLimitResult;
    }

    public LoginResponseDTO getLoginResponse() {
        return loginResponse;
    }

    public RateLimitResult getRateLimitResult() {
        return rateLimitResult;
    }
}