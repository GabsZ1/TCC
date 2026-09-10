import axios from 'axios'

export interface ApiErrorBody {
  status: number
  message: string
  fields?: Record<string, string>
  timestamp: string
}

/**
 * Extrai uma mensagem amigável de um erro do axios, considerando o formato
 * padrão de erro da API (br.com.salonhub.dto.ApiError).
 */
export function getApiErrorMessage(error: unknown, fallback = 'Ocorreu um erro inesperado.') {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiErrorBody | undefined
    if (data?.fields && Object.keys(data.fields).length > 0) {
      return Object.values(data.fields).join(' ')
    }
    if (data?.message) {
      return data.message
    }
  }
  return fallback
}
