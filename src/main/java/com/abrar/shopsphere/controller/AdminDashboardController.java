package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.AdminDashboardSummaryDTO;
import com.abrar.shopsphere.dto.AdminPaymentHistoryDTO;
import com.abrar.shopsphere.service.AdminDashboardService;
import com.abrar.shopsphere.dto.AdminOrderDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(
            AdminDashboardService dashboardService
    ) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminDashboardSummaryDTO> summary() {
        return ResponseEntity.ok(
                dashboardService.getSummary()
        );
    }

    @GetMapping("/payment-history")
    public ResponseEntity<List<AdminPaymentHistoryDTO>> paymentHistory(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(
                dashboardService.getPaymentHistory(limit)
        );
    }
    @GetMapping("/orders")
    public ResponseEntity<List<AdminOrderDTO>> recentOrders(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(
                dashboardService.getRecentOrders(limit)
        );
    }
}