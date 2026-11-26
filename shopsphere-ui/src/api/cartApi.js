
import apiClient from './client';

export async function getCart() {
  const response = await apiClient.get('/cart');
  return response.data;
}

export async function addCartItem(productId, quantity = 1, variantId = null) {
  const payload = {
    productId: Number(productId),
    quantity: Number(quantity),
  };

  if (variantId !== null && variantId !== undefined && variantId !== '') {
    payload.variantId = Number(variantId);
  }

  const response = await apiClient.post('/cart/items', payload);
  return response.data;
}

export async function updateCartItem(productId, quantity) {
  const response = await apiClient.put(`/cart/items/${productId}`, {
    quantity,
  });

  return response.data;
}

export async function removeCartItem(productId) {
  const response = await apiClient.delete(`/cart/items/${productId}`);
  return response.data;
}

export async function clearCart() {
  const response = await apiClient.delete('/cart');
  return response.data;
}
