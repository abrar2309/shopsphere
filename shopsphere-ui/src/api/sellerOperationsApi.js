import apiClient from './client';

export async function getInventorySummary() {
    const response = await apiClient.get('/seller/inventory/summary');
    return response.data;
}

export async function getInventoryAlerts() {
    const response = await apiClient.get('/seller/inventory/alerts');
    return response.data;
}

export async function getInventoryMovements(limit = 50) {
    const response = await apiClient.get('/seller/inventory/movements', {
        params: { limit },
    });
    return response.data;
}

export async function adjustInventory(productId, quantityChange, reason, note) {
    const response = await apiClient.post(
        `/seller/inventory/products/${encodeURIComponent(productId)}/adjustments`,
        { quantityDelta: quantityChange, reason, note }
    );
    return response.data;
}

export async function updateInventoryThreshold(productId, lowStockThreshold) {
    const response = await apiClient.put(
        `/seller/inventory/products/${encodeURIComponent(productId)}/threshold`,
        { lowStockThreshold }
    );
    return response.data;
}

export async function getSellerSales(period = 'DAILY') {
    const response = await apiClient.get('/seller/analytics/sales', {
        params: { period },
    });
    return response.data;
}
