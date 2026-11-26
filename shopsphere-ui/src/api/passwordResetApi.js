
import apiClient from './client';

export async function requestPasswordReset(email) {
    const response = await apiClient.post('/auth/password-reset/request', {
        email,
    });

    return response.data;
}

export async function confirmPasswordReset(email, otp, newPassword) {
    const response = await apiClient.post('/auth/password-reset/confirm', {
        email,
        otp,
        newPassword,
    });

    return response.data;
}
