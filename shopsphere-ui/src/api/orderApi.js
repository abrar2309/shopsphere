
import apiClient from './client';

// Create an order for the logged-in customer.
export async function createOrder(items, addressId) {
  if (!Array.isArray(items) || items.length === 0) {
    throw new Error('At least one order item is required.');
  }

  if (
      addressId === null ||
      addressId === undefined ||
      addressId === ''
  ) {
    throw new Error('Address ID is required.');
  }

  const response = await apiClient.post('/orders', {
    items,
    addressId,
  });

  return response.data;
}

// Get all orders belonging to the logged-in customer.
export async function getMyOrders() {
  const response = await apiClient.get('/orders');

  return response.data;
}

// Get one order by ID.
export async function getOrderById(orderId) {
  if (
      orderId === null ||
      orderId === undefined ||
      orderId === ''
  ) {
    throw new Error('Order ID is required.');
  }

  const response = await apiClient.get(
      `/orders/${encodeURIComponent(orderId)}`
  );

  return response.data;
}
