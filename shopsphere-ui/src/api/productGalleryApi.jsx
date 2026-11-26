import apiClient from './client';

export async function getProductImages(productId) {
    const response = await apiClient.get(
        `/products/${encodeURIComponent(productId)}/images`
    );
    return response.data;
}

export async function uploadProductImages(productId, files) {
    if (!productId) {
        throw new Error('Product ID is required.');
    }

    if (!files?.length) {
        throw new Error('Select at least one image.');
    }

    if (files.length > 10) {
        throw new Error('You can upload a maximum of 10 images at once.');
    }

    const formData = new FormData();

    Array.from(files).forEach((file) => {
        formData.append('files', file);
    });

    const response = await apiClient.post(
        `/products/${encodeURIComponent(productId)}/images`,
        formData
    );

    return response.data;
}

export async function setPrimaryProductImage(productId, imageId) {
    const response = await apiClient.put(
        `/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}/primary`
    );

    return response.data;
}

export async function deleteProductImage(productId, imageId) {
    const response = await apiClient.delete(
        `/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}`
    );

    return response.data;
}