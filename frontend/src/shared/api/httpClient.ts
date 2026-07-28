import axios from 'axios'

const HTTP_TIMEOUT_MS = 10_000

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: HTTP_TIMEOUT_MS,
  withCredentials: true,
})
