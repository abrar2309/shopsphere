
import apiClient from './client';

// Fetch products using pagination, sorting and optional filters.
// Supports both options-object and legacy positional arguments.
export async function getProducts(
    options = {},
    legacySize,
    legacySortBy,
    legacyDirection
) {
  let params;

  if (typeof options === 'object' && options !== null) {
    const {
      page = 0,
      size = 20,
      sortBy = 'id',
      direction = 'asc',
      search,
      category,
      minPrice,
      maxPrice,
    } = options;

    params = {
      page,
      size,
      sortBy,
      direction,
    };

    if (search) params.search = search;
    if (category) params.category = category;
  if (minPrice !== undefined && minPrice !== null && minPrice !== '') {
    params.minPrice = minPrice;
  }
  if (maxPrice !== undefined && maxPrice !== null && maxPrice !== '') {
    params.maxPrice = maxPrice;
  }
    if (minPrice !== undefined) params.minPrice = minPrice;
    if (maxPrice !== undefined) params.maxPrice = maxPrice;
  } else {
    params = {
      page: options ?? 0,
      size: legacySize ?? 20,
      sortBy: legacySortBy ?? 'id',
      direction: legacyDirection ?? 'asc',
    };
  }

  const response = await apiClient.get('/products', {
    params,
  });

  return response.data;
}

// Fetch a page using an options object.
export async function getProductPage(options = {}) {
  return getProducts(options);
}

// Fetch one product by ID.
export async function getProductById(productId) {
  if (
      productId === null ||
      productId === undefined ||
      productId === ''
  ) {
    throw new Error('Product ID is required.');
  }

  const response = await apiClient.get(
      `/products/${encodeURIComponent(productId)}`
  );

  return response.data;
}

// Fetch products belonging to a category.
export async function getProductsByCategory(categoryName) {
  if (!categoryName || !categoryName.trim()) {
    throw new Error('Category name is required.');
  }

  const response = await apiClient.get('/products/category', {
    params: {
      categoryName: categoryName.trim(),
    },
  });

  return response.data;
}

// Fetch categories stored in the backend.
export async function getCategories() {
  const response = await apiClient.get('/categories');

  return response.data;
}

// Search products by name.
export async function searchProducts(name) {
  if (!name || !name.trim()) {
    return [];
  }

  const response = await apiClient.get('/products/search', {
    params: {
      name: name.trim(),
    },
  });

  return response.data;
}

// BGE semantic search with backend-enforced category and price filters.
export async function searchBgeProducts(query, limit = 20, category, minPrice, maxPrice) {
  const cleanedQuery = String(query ?? '').trim();

  if (!cleanedQuery) return [];

  if (cleanedQuery.length > 500) {
    throw new Error('Search query cannot exceed 500 characters.');
  }

  const params = {
    query: cleanedQuery,
    limit: Math.min(50, Math.max(1, Number(limit) || 50)),
  };

  if (category) params.category = category;
  if (minPrice !== undefined && minPrice !== null && minPrice !== '') {
    params.minPrice = Number(minPrice);
  }
  if (maxPrice !== undefined && maxPrice !== null && maxPrice !== '') {
    params.maxPrice = Number(maxPrice);
  }

  const response = await apiClient.get('/ai/bge/products/search', { params });
  return Array.isArray(response.data) ? response.data : [];
}
// Filter products by price range.
export async function getProductsByPriceRange(minPrice, maxPrice) {
  if (
      minPrice === null ||
      minPrice === undefined ||
      maxPrice === null ||
      maxPrice === undefined
  ) {
    throw new Error('Both minimum and maximum prices are required.');
  }

  if (Number(minPrice) > Number(maxPrice)) {
    throw new Error('Minimum price cannot exceed maximum price.');
  }

  const response = await apiClient.get('/products/price', {
    params: {
      minPrice,
      maxPrice,
    },
  });

  return response.data;
}

// Resolve a backend image path into a browser-accessible URL.
export function getProductImageUrl(imagePath) {
  if (!imagePath) {
    return '';
  }

  if (/^https?:\/\//i.test(imagePath)) {
    return imagePath;
  }

  const apiBaseUrl = (
      import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'
  ).replace(/\/$/, '');

  const normalizedPath = imagePath.startsWith('/')
      ? imagePath
      : `/${imagePath}`;

  return `${apiBaseUrl}${normalizedPath}`;
}

// Upload or replace a product image.
// Backend expects multipart field "file".
export async function uploadProductImage(productId, file) {
  if (
      productId === null ||
      productId === undefined ||
      productId === ''
  ) {
    throw new Error('Product ID is required.');
  }

  if (!(file instanceof File)) {
    throw new Error('Please select a valid image file.');
  }

  const formData = new FormData();

  formData.append('file', file);

  // Do not manually set Content-Type.
  // client.js removes the global JSON header for FormData.
  const response = await apiClient.post(
      `/products/${encodeURIComponent(productId)}/image`,
      formData
  );

  return response.data;
}
