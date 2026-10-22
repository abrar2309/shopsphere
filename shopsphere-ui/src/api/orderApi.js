import apiClient from './client';

// The backend derives customer identity and prices from the authenticated session
// and database. Never send a client-calculated total or user ID.
export async function createOrder(items) {
  const response = await apiClient.post('/orders', { items });
  return response.data;
}

export async function getMyOrders() {
  const response = await apiClient.get('/orders');
  return response.data;
}
