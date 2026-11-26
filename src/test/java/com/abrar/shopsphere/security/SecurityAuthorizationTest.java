
package com.abrar.shopsphere.security;

import com.abrar.shopsphere.ShopSphereTestSchedulingConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ShopSphereTestSchedulingConfig.class)
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    // Test 1: Unauthenticated user cannot access admin dashboard.
    @Test
    void unauthenticatedUserCannotAccessAdminDashboard()
            throws Exception {

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    // Test 2: Customer cannot access admin dashboard.
    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void customerCannotAccessAdminDashboard()
            throws Exception {

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    // Test 3: Seller cannot access admin dashboard.
    @Test
    @WithMockUser(username = "seller@test.com", roles = "SELLER")
    void sellerCannotAccessAdminDashboard()
            throws Exception {

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    // Test 4: Admin can access admin dashboard.
    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void adminCanAccessAdminDashboard()
            throws Exception {

        mockMvc.perform(get("/admin/dashboard")
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string("Welcome Admin!"));
    }
}
