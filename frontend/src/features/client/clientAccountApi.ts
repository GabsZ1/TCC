/** Consulta e altera o perfil e o encerramento da conta do cliente. */
import { api } from '@/lib/api'

export interface ClientProfile {
  id: number
  name: string
  email: string
  phone: string | null
}

export interface ClientProfileInput {
  name: string
  email: string
  phone: string
}

export function fetchClientProfile() {
  return api.get<ClientProfile>('/clientes/me').then((response) => response.data)
}

export function updateClientProfile(input: ClientProfileInput) {
  return api.put<ClientProfile>('/clientes/me', input).then((response) => response.data)
}

export function closeClientAccount(password: string) {
  return api.delete<void>('/clientes/me', { data: { password } })
}
