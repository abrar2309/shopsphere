import axios from 'axios';

const apiClient = axios.create({
baseURL: import.meta.env.VITE_API_BASE_URL || '',  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

apiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('shopsphere_token');

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

export default apiClient;