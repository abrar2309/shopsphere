
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

export async function adjustInventory(productId, payload) {
    const response = await apiClient.post(
        `/seller/inventory/products/${productId}/adjustments`,
        payload
    );
    return response.data;
}

export async function updateInventoryThreshold(productId, threshold) {
    const response = await apiClient.put(
        `/seller/inventory/products/${productId}/threshold`,
        { threshold }
    );
    return response.data;
}
