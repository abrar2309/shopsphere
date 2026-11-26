import apiClient from './client';

export async function getWishlist() {
    const response = await apiClient.get('/wishlist');
    return response.data;
}

export async function addWishlistItem(productId) {
    const response = await apiClient.post('/wishlist/items', {
        productId,
    });
    return response.data;
}

export async function removeWishlistItem(productId) {
    const response = await apiClient.delete(
        `/wishlist/items/${encodeURIComponent(productId)}`
    );
    return response.data;
}

// Compatibility alias for the Wishlist page.
export async function removeFromWishlist(productId) {
    return removeWishlistItem(productId);
}