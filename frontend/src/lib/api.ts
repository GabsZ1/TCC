import axios from 'axios'

/**
 * Cliente HTTP central da aplicação.
 * Em dev, o Vite faz proxy de /api para http://localhost:8080 (ver vite.config.ts).
 * Em produção, defina VITE_API_URL no .env.production.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

const TOKEN_STORAGE_KEY = 'salonhub:token'

// Rotas públicas: um token antigo/expirado guardado no navegador não pode ser
// enviado nelas, senão o backend recusa a requisição com 401 antes de processá-la.
const PUBLIC_AUTH_PATHS = [
  '/auth/login',
  '/auth/cadastro',
  '/auth/clientes',
  '/auth/primeiro-acesso',
  '/auth/recuperar-senha',
  '/auth/redefinir-senha',
]

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  const isPublicAuthPath = PUBLIC_AUTH_PATHS.some((path) => config.url?.startsWith(path))
  if (token && !isPublicAuthPath) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Uma resposta atrasada de uma sessão anterior não pode apagar um token
      // que tenha sido salvo por um login mais recente.
      const requestAuthorization = error.config?.headers?.Authorization
      const currentToken = localStorage.getItem(TOKEN_STORAGE_KEY)
      const currentAuthorization = currentToken ? `Bearer ${currentToken}` : null

      if (!currentAuthorization || requestAuthorization === currentAuthorization) {
        localStorage.removeItem(TOKEN_STORAGE_KEY)
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(error)
  },
)

export function setStoredToken(token: string) {
  localStorage.setItem(TOKEN_STORAGE_KEY, token)
}

export function getStoredToken() {
  return localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function clearStoredToken() {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}
