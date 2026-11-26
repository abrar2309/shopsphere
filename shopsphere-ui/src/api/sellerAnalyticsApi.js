
import apiClient from './client';

export async function getSellerAnalyticsSummary(days = 30) {
    const response = await apiClient.get('/seller/analytics/summary', {
        params: { days },
    });
    return response.data;
}

export async function getSellerSales(period = 'DAILY', days = 30) {
    const response = await apiClient.get('/seller/analytics/sales', {
        params: { period, days },
    });
    return response.data;
}
