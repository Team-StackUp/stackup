export const env = {
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  SSE_BASE_URL: import.meta.env.VITE_SSE_BASE_URL ?? 'http://localhost:8080',
} as const
