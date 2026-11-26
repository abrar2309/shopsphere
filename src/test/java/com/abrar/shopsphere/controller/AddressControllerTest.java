
package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.ShopSphereTestSchedulingConfig;
import com.abrar.shopsphere.dto.AddressRequestDTO;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.AddressRepository;
import com.abrar.shopsphere.repository.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ShopSphereTestSchedulingConfig.class)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        addressRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();

        user.setFirstName("Test");
        user.setLastName("Customer");
        user.setEmail("customer@test.com");
        user.setPhoneNumber("9876543210");
        user.setPassword("test-password");
        user.setRole("CUSTOMER");

        userRepository.save(user);
    }

    private AddressRequestDTO validRequest() {

        AddressRequestDTO request = new AddressRequestDTO();

        request.setLabel("Home");
        request.setRecipientName("Test Customer");
        request.setPhoneNumber("9876543210");
        request.setAddressLine1("123 Main Street");
        request.setAddressLine2("Apartment 4");
        request.setCity("Bengaluru");
        request.setState("Karnataka");
        request.setPostalCode("560001");
        request.setCountry("India");
        request.setIsDefault(false);

        return request;
    }

    @Test
    void unauthenticatedUserCannotGetAddresses() throws Exception {

        mockMvc.perform(get("/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void customerCanGetOwnAddresses() throws Exception {

        mockMvc.perform(get("/addresses"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void customerCanCreateAddress() throws Exception {

        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Home"))
                .andExpect(jsonPath("$.city").value("Bengaluru"))
                .andExpect(jsonPath("$.default").value(true));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void invalidAddressShouldReturnBadRequest() throws Exception {

        AddressRequestDTO request = validRequest();

        request.setCity("");

        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedUserCannotCreateAddress() throws Exception {

        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }
}
