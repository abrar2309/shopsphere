
import apiClient from './client';

export async function getPersonalizedRecommendations(limit = 8) {
    const response = await apiClient.get(
        '/ai/recommendations/personalized',
        {
            params: { limit },
        }
    );

    return Array.isArray(response.data)
        ? response.data
        : [];
}
