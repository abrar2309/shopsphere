
package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.SellerAnalyticsSummaryDTO;
import com.abrar.shopsphere.dto.SellerSalesPointDTO;
import com.abrar.shopsphere.service.SellerAnalyticsService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/seller/analytics")
@PreAuthorize("hasRole('SELLER')")
@Validated
public class SellerAnalyticsController {

    private final SellerAnalyticsService analytics;

    public SellerAnalyticsController(
            SellerAnalyticsService analytics
    ) {
        this.analytics = analytics;
    }

    @GetMapping("/sales")
    public ResponseEntity<List<SellerSalesPointDTO>> sales(
            @RequestParam(defaultValue = "DAILY") String period,
            @RequestParam(defaultValue = "30")
            @Min(7) @Max(365) int days
    ) {
        return ResponseEntity.ok(
                analytics.getSales(period, days)
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<SellerAnalyticsSummaryDTO> summary(
            @RequestParam(defaultValue = "30")
            @Min(7) @Max(365) int days
    ) {
        return ResponseEntity.ok(
                analytics.getSummary(days)
        );
    }
}
