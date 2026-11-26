
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.AddressRequestDTO;
import com.abrar.shopsphere.dto.AddressResponseDTO;
import com.abrar.shopsphere.entity.Address;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.AddressRepository;
import com.abrar.shopsphere.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(
            AddressRepository addressRepository,
            UserRepository userRepository) {

        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    // ============================================================
    // CREATE ADDRESS
    // ============================================================

    @Transactional
    public AddressResponseDTO createAddress(
            String email,
            AddressRequestDTO request) {

        User user = findAndLockUser(email);

        List<Address> existingAddresses =
                addressRepository.findAllByUserIdOrderByIdDesc(user.getId());

        Address address = new Address();

        address.setUser(user);
        applyRequest(address, request);

        boolean makeDefault =
                existingAddresses.isEmpty()
                        || Boolean.TRUE.equals(request.getIsDefault());

        if (makeDefault) {
            clearDefaultAddresses(existingAddresses);
        }

        address.setDefault(makeDefault);

        Address savedAddress = addressRepository.save(address);

        return mapToResponse(savedAddress);
    }

    // ============================================================
    // GET ALL CURRENT USER ADDRESSES
    // ============================================================

    @Transactional(readOnly = true)
    public List<AddressResponseDTO> getMyAddresses(String email) {

        User user = findUserByEmail(email);

        return addressRepository
                .findAllByUserIdOrderByIdDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ============================================================
    // GET ONE CURRENT USER ADDRESS
    // ============================================================

    @Transactional(readOnly = true)
    public AddressResponseDTO getMyAddress(
            String email,
            Long addressId) {

        User user = findUserByEmail(email);

        Address address = findAddress(addressId, user.getId());

        return mapToResponse(address);
    }

    // ============================================================
    // UPDATE CURRENT USER ADDRESS
    // ============================================================

    @Transactional
    public AddressResponseDTO updateAddress(
            String email,
            Long addressId,
            AddressRequestDTO request) {

        User user = findAndLockUser(email);

        Address address = findAddress(addressId, user.getId());

        boolean wasDefault = address.isDefault();

        applyRequest(address, request);

        if (Boolean.TRUE.equals(request.getIsDefault())) {

            List<Address> addresses =
                    addressRepository.findAllByUserIdOrderByIdDesc(
                            user.getId()
                    );

            clearDefaultAddresses(addresses);
            address.setDefault(true);

        } else if (Boolean.FALSE.equals(request.getIsDefault())
                && wasDefault) {

            // Do not leave the customer without a default address
            // when another saved address exists.

            List<Address> addresses =
                    addressRepository.findAllByUserIdOrderByIdDesc(
                            user.getId()
                    );

            Address replacement = addresses.stream()
                    .filter(other -> !other.getId().equals(addressId))
                    .findFirst()
                    .orElse(null);

            if (replacement != null) {
                address.setDefault(false);
                replacement.setDefault(true);
            } else {
                address.setDefault(true);
            }
        }

        return mapToResponse(address);
    }

    // ============================================================
    // DELETE CURRENT USER ADDRESS
    // ============================================================

    @Transactional
    public void deleteAddress(String email, Long addressId) {

        User user = findAndLockUser(email);

        Address address = findAddress(addressId, user.getId());

        boolean wasDefault = address.isDefault();

        addressRepository.delete(address);

        // Ensure the deleted row is removed before selecting
        // a replacement default address.
        addressRepository.flush();

        if (wasDefault) {

            Address replacement = addressRepository
                    .findFirstByUserIdOrderByIdAsc(user.getId())
                    .orElse(null);

            if (replacement != null) {
                replacement.setDefault(true);
            }
        }
    }

    // ============================================================
    // SET DEFAULT ADDRESS
    // ============================================================

    @Transactional
    public AddressResponseDTO setDefaultAddress(
            String email,
            Long addressId) {

        User user = findAndLockUser(email);

        Address selectedAddress =
                findAddress(addressId, user.getId());

        List<Address> addresses =
                addressRepository.findAllByUserIdOrderByIdDesc(
                        user.getId()
                );

        clearDefaultAddresses(addresses);

        selectedAddress.setDefault(true);

        return mapToResponse(selectedAddress);
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private User findUserByEmail(String email) {

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user account not found."
                        ));
    }

    private User findAndLockUser(String email) {

        User user = findUserByEmail(email);

        return userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user account not found."
                        ));
    }

    private Address findAddress(Long addressId, Long userId) {

        return addressRepository
                .findByIdAndUserId(addressId, userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Address not found."
                        ));
    }

    private void clearDefaultAddresses(List<Address> addresses) {

        addresses.forEach(address ->
                address.setDefault(false)
        );
    }

    private void applyRequest(
            Address address,
            AddressRequestDTO request) {

        address.setLabel(request.getLabel().trim());
        address.setRecipientName(request.getRecipientName().trim());
        address.setPhoneNumber(request.getPhoneNumber().trim());
        address.setAddressLine1(request.getAddressLine1().trim());

        address.setAddressLine2(
                request.getAddressLine2() == null
                        || request.getAddressLine2().isBlank()
                        ? null
                        : request.getAddressLine2().trim()
        );

        address.setCity(request.getCity().trim());
        address.setState(request.getState().trim());
        address.setPostalCode(request.getPostalCode().trim());
        address.setCountry(request.getCountry().trim());
    }

    private AddressResponseDTO mapToResponse(Address address) {

        return new AddressResponseDTO(
                address.getId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.isDefault()
        );
    }
}
