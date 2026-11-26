import apiClient from './client';
import { getProducts } from './productApi';
export async function getAdminDashboardSummary() {
    const response = await apiClient.get('/admin/dashboard/summary');
    return response.data;
}

export async function getAdminPaymentHistory(limit = 50) {
    const response = await apiClient.get('/admin/dashboard/payment-history', {
        params: { limit },
    });
    return response.data;
}

export async function getAdminUsers() {
    const response = await apiClient.get('/users');
    return response.data;
}
export async function getAdminRecentOrders(limit = 50) {
    const response = await apiClient.get('/admin/dashboard/orders', {
        params: { limit },
    });
    return response.data;
}
export async function deleteAdminUser(userId) {
    await apiClient.delete(`/users/${encodeURIComponent(userId)}`);
}

export async function getAdminCategories() {
    const response = await apiClient.get('/categories');
    return response.data;
}
export async function createAdminCategory(name) {
    const response = await apiClient.post('/categories', { name });
    return response.data;
}

export async function updateAdminCategory(id, name) {
    const response = await apiClient.put(`/categories/${id}`, { name });
    return response.data;
}

export async function deleteAdminCategory(id) {
    await apiClient.delete(`/categories/${id}`);
}

export async function getAdminProductCatalog(page = 0, size = 50) {
    return getProducts({
        page,
        size,
        sortBy: 'id',
        direction: 'desc',
    });
}