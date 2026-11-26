
import apiClient from './client';
import { uploadProductImage } from './productApi';

// Get products belonging to the authenticated seller.
export async function getMyProducts() {
    const response = await apiClient.get('/products/mine');
    return response.data;
}

// Create a new product.
export async function createSellerProduct(product) {
    const response = await apiClient.post('/products', product);
    return response.data;
}

// Update an existing product.
export async function updateSellerProduct(productId, product) {
    const response = await apiClient.put(
        `/products/${encodeURIComponent(productId)}`,
        product
    );

    return response.data;
}

// Delete a seller-owned product.
export async function deleteSellerProduct(productId) {
    await apiClient.delete(
        `/products/${encodeURIComponent(productId)}`
    );
}

// Reuse the existing image-upload implementation.
export { uploadProductImage };
