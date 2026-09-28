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
    if (!error.response) {
      return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.'
    }
    const data = error.response.data as Partial<ApiErrorBody> | undefined
    // Só exibimos mensagens no formato de erro da nossa API (que sempre traz "fields").
    // Respostas técnicas de outras camadas (proxy, servidor fora do ar, erro padrão do
    // Spring) caem no texto amigável da tela.
    if (!isApiErrorBody(data)) {
      return fallback
    }
    if (data.fields && Object.keys(data.fields).length > 0) {
      return Object.values(data.fields).join(' ')
    }
    if (data.message) {
      return data.message
    }
  }
  return fallback
}

function isApiErrorBody(data: unknown): data is ApiErrorBody {
  return typeof data === 'object'
    && data !== null
    && 'fields' in data
    && typeof (data as ApiErrorBody).message === 'string'
}
