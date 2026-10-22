package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.UserRequestDTO;
import com.abrar.shopsphere.dto.UserResponseDTO;
import com.abrar.shopsphere.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Create User
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO addUser(
            @Valid
            @RequestBody UserRequestDTO requestDTO) {

        return userService.addUser(requestDTO);
    }

    // Get All Users
    @GetMapping
    public List<UserResponseDTO> getAllUsers() {

        return userService.getAllUsers();
    }

    // Get User By Id
    @GetMapping("/{id}")
    public UserResponseDTO getUserById(
            @PathVariable Long id) {

        return userService.getUserById(id);
    }

    // Update User
    @PutMapping("/{id}")
    public UserResponseDTO updateUser(

            @PathVariable Long id,

            @Valid
            @RequestBody UserRequestDTO requestDTO) {

        return userService.updateUser(id, requestDTO);
    }

    // Delete User
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(
            @PathVariable Long id) {

        userService.deleteUser(id);
    }
}