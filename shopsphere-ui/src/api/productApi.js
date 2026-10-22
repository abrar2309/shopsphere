
import apiClient from './client';

// Fetch a page of products from the backend.
export async function getProducts(page = 0, size = 20) {
  const response = await apiClient.get('/products', {
    params: { page, size },
  });

  return response.data;
}

// Fetch one product by its ID.
export async function getProductById(productId) {
  const response = await apiClient.get(`/products/${productId}`);
  return response.data;
}

// Fetch products belonging to a category.
export async function getProductsByCategory(categoryName) {
  const response = await apiClient.get('/products/category', {
    params: { categoryName },
  });

  return response.data;
}

// Fetch categories stored in the backend database.
export async function getCategories() {
  const response = await apiClient.get('/categories');
  return response.data;
}

// Search products by name.
export async function searchProducts(name) {
  const response = await apiClient.get('/products/search', {
    params: { name },
  });

  return response.data;
}
