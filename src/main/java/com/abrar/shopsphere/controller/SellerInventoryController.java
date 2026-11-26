package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.InventoryAdjustmentRequest;
import com.abrar.shopsphere.dto.InventoryAlertDTO;
import com.abrar.shopsphere.dto.InventoryMovementDTO;
import com.abrar.shopsphere.dto.LowStockThresholdRequest;
import com.abrar.shopsphere.dto.SellerInventorySummaryDTO;
import com.abrar.shopsphere.service.SellerInventoryService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seller/inventory")
@PreAuthorize("hasRole('SELLER')")
public class SellerInventoryController {

    private final SellerInventoryService inventoryService;

    public SellerInventoryController(
            SellerInventoryService inventoryService) {

        this.inventoryService = inventoryService;
    }

    // GET /seller/inventory/summary
    @GetMapping("/summary")
    public ResponseEntity<SellerInventorySummaryDTO> getSummary() {

        return ResponseEntity.ok(
                inventoryService.getSummary()
        );
    }

    // GET /seller/inventory/alerts
    @GetMapping("/alerts")
    public ResponseEntity<List<InventoryAlertDTO>> getLowStockAlerts() {

        return ResponseEntity.ok(
                inventoryService.getLowStockProducts()
        );
    }

    // GET /seller/inventory/movements?limit=50
    @GetMapping("/movements")
    public ResponseEntity<List<InventoryMovementDTO>> getMovements(
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(
                inventoryService.getMovements(limit)
        );
    }

    // POST /seller/inventory/products/{productId}/adjustments
    @PostMapping("/products/{productId}/adjustments")
    public ResponseEntity<InventoryMovementDTO> adjustInventory(
            @PathVariable Long productId,
            @Valid @RequestBody InventoryAdjustmentRequest request) {

        InventoryMovementDTO result =
                inventoryService.adjustInventory(productId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }

    // PUT /seller/inventory/products/{productId}/threshold
    @PutMapping("/products/{productId}/threshold")
    public ResponseEntity<Void> updateThreshold(
            @PathVariable Long productId,
            @Valid @RequestBody LowStockThresholdRequest request) {

        inventoryService.updateLowStockThreshold(productId, request);

        return ResponseEntity.noContent().build();
    }
}