import { api } from '@/lib/api'

export interface Establishment {
  id: number
  name: string
  cnpj: string
  email: string | null
  phone: string | null
  openingTime: string
  closingTime: string
  active: boolean
}

export interface EstablishmentUpdateInput {
  name: string
  email: string
  phone: string
}

export function fetchMyEstablishment() {
  return api.get<Establishment>('/estabelecimentos/me').then((response) => response.data)
}

export function updateMyEstablishment(input: EstablishmentUpdateInput) {
  return api.put<Establishment>('/estabelecimentos/me', input).then((response) => response.data)
}

export function deactivateMyEstablishment() {
  return api.delete('/estabelecimentos/me')
}