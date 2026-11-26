
package com.abrar.shopsphere.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SellerSalesPointDTO(
        LocalDate periodStart,
        Long paidOrders,
        Long unitsSold,
        BigDecimal grossSales
) {
}
