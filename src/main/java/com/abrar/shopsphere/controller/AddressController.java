
package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.AddressRequestDTO;
import com.abrar.shopsphere.dto.AddressResponseDTO;
import com.abrar.shopsphere.service.AddressService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    // Add a new address
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponseDTO createAddress(
            Authentication authentication,
            @Valid @RequestBody AddressRequestDTO request) {

        return addressService.createAddress(
                authentication.getName(),
                request
        );
    }

    // Retrieve all addresses belonging to the current user
    @GetMapping
    public List<AddressResponseDTO> getMyAddresses(
            Authentication authentication) {

        return addressService.getMyAddresses(
                authentication.getName()
        );
    }

    // Retrieve one address belonging to the current user
    @GetMapping("/{addressId}")
    public AddressResponseDTO getMyAddress(
            Authentication authentication,
            @PathVariable Long addressId) {

        return addressService.getMyAddress(
                authentication.getName(),
                addressId
        );
    }

    // Update an address
    @PutMapping("/{addressId}")
    public AddressResponseDTO updateAddress(
            Authentication authentication,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequestDTO request) {

        return addressService.updateAddress(
                authentication.getName(),
                addressId,
                request
        );
    }

    // Delete an address
    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(
            Authentication authentication,
            @PathVariable Long addressId) {

        addressService.deleteAddress(
                authentication.getName(),
                addressId
        );
    }

    // Set an address as default
    @PatchMapping("/{addressId}/default")
    public AddressResponseDTO setDefaultAddress(
            Authentication authentication,
            @PathVariable Long addressId) {

        return addressService.setDefaultAddress(
                authentication.getName(),
                addressId
        );
    }
}
