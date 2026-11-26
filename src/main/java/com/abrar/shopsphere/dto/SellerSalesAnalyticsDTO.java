package com.abrar.shopsphere.dto;

import java.util.List;

public record SellerSalesAnalyticsDTO(
        String period,
        List<SellerSalesPointDTO> data
) {
}