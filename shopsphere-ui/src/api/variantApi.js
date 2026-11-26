import apiClient from './client';

export async function getProductVariants(productId) {
    const response = await apiClient.get(`/products/${encodeURIComponent(productId)}/variants`);
    return response.data;
}

export async function createProductVariant(productId, variant) {
    const response = await apiClient.post(`/products/${encodeURIComponent(productId)}/variants`, variant);
    return response.data;
}

export async function updateProductVariant(productId, variantId, variant) {
    const response = await apiClient.put(`/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`, variant);
    return response.data;
}

export async function deactivateProductVariant(productId, variantId) {
    const response = await apiClient.delete(`/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`);
    return response.data;
}
// variant-image-gallery-patch-v1
export async function getVariantImages(productId, variantId) {
    const response = await apiClient.get(`/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}/images`);
    return response.data;
}
export async function uploadVariantImages(productId, variantId, files) {
    if (!files?.length) throw new Error('Select at least one image.');
    if (files.length > 10) throw new Error('Upload at most 10 images at a time.');
    const body = new FormData();
    Array.from(files).forEach((file) => body.append('files', file));
    const response = await apiClient.post(`/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}/images`, body);
    return response.data;
}
export async function deleteVariantImage(productId, variantId, imageId) {
    await apiClient.delete(`/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}/images/${encodeURIComponent(imageId)}`);
}