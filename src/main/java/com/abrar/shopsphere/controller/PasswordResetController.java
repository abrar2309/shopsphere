package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/request")
    public ResponseEntity<Message> request(
            @Valid @RequestBody Request dto
    ) {
        service.request(dto.email());

        return ResponseEntity.ok(
                new Message(
                        "If the account exists, a reset code will be sent to its registered email and WhatsApp number."
                )
        );
    }

    @PostMapping("/confirm")
    public ResponseEntity<Message> confirm(
            @Valid @RequestBody Confirm dto
    ) {
        boolean updated = service.confirm(
                dto.email(),
                dto.otp(),
                dto.newPassword()
        );

        if (!updated) {
            return ResponseEntity.badRequest().body(
                    new Message("Invalid or expired reset code.")
            );
        }

        return ResponseEntity.ok(
                new Message("Password updated successfully. You can now log in.")
        );
    }

    public record Request(
            @NotBlank @Email String email
    ) {}

    public record Confirm(
            @NotBlank @Email String email,
            @NotBlank String otp,
            @NotBlank @Size(min = 10, max = 100) String newPassword
    ) {}

    public record Message(String message) {}
}