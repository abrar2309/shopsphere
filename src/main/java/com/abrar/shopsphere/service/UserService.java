
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.UserProfileUpdateDTO;
import com.abrar.shopsphere.dto.UserResponseDTO;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.exception.UserNotFoundException;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ADMIN ONLY: Retrieve all users
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ADMIN ONLY: Retrieve any user
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long id) {

        User user = findUser(id);

        return mapToResponse(user);
    }

    // CUSTOMER / SELLER / ADMIN: Retrieve own profile
    @Transactional(readOnly = true)
    public UserResponseDTO getCurrentUser(String email) {

        User user = findUserByEmail(email);

        return mapToResponse(user);
    }

    // CUSTOMER / SELLER / ADMIN: Update own profile
    @Transactional
    public UserResponseDTO updateCurrentUser(
            String email,
            UserProfileUpdateDTO dto) {

        User user = findUserByEmail(email);

        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhoneNumber(dto.getPhoneNumber());

        User updatedUser = userRepository.save(user);

        return mapToResponse(updatedUser);
    }

    // ADMIN ONLY: Delete a user
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteUser(Long id) {

        User user = findUser(id);

        userRepository.delete(user);
    }

    private User findUser(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException(id));
    }

    private User findUserByEmail(String email) {

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user account not found."
                        ));
    }

    private UserResponseDTO mapToResponse(User user) {

        return new UserResponseDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber()
        );
    }
}
