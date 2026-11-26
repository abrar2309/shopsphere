
package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.UserProfileUpdateDTO;
import com.abrar.shopsphere.dto.UserResponseDTO;
import com.abrar.shopsphere.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponseDTO getMyProfile(
            Authentication authentication) {

        return userService.getCurrentUser(
                authentication.getName()
        );
    }

    @PutMapping("/me")
    public UserResponseDTO updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateDTO dto) {

        return userService.updateCurrentUser(
                authentication.getName(),
                dto
        );
    }

    // ADMIN ONLY
    @GetMapping
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    // ADMIN ONLY
    @GetMapping("/{id:[0-9]+}")
    public UserResponseDTO getUserById(
            @PathVariable Long id) {

        return userService.getUserById(id);
    }

    // ADMIN ONLY
    @DeleteMapping("/{id:[0-9]+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
