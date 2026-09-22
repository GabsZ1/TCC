import { api } from '@/lib/api'

export interface SystemEstablishment {
  id: number
  name: string
  cnpj: string
  email: string | null
  phone: string | null
  active: boolean
  createdAt: string
}

export function fetchSystemEstablishments() {
  return api.get<SystemEstablishment[]>('/admin-sistema/estabelecimentos').then((response) => response.data)
}

export function deactivateSystemEstablishment(establishmentId: number) {
  return api.delete(`/admin-sistema/estabelecimentos/${establishmentId}`)
}
