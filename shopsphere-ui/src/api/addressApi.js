
import apiClient from './client';

export async function getAddresses() {
    const response = await apiClient.get('/addresses');
    return response.data;
}

export async function createAddress(address) {
    const response = await apiClient.post('/addresses', address);
    return response.data;
}

export async function updateAddress(addressId, address) {
    const response = await apiClient.put(`/addresses/${addressId}`, address);
    return response.data;
}

export async function deleteAddress(addressId) {
    await apiClient.delete(`/addresses/${addressId}`);
}

export async function setDefaultAddress(addressId) {
    const response = await apiClient.patch(`/addresses/${addressId}/default`);
    return response.data;
}
