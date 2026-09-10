import { api } from '@/lib/api'

export interface SalonService {
  id: number
  name: string
  description: string | null
  price: number
  commissionPercentage: number
  durationMinutes: number
  active: boolean
}

export interface SalonServiceInput {
  name: string
  description?: string
  price: number
  commissionPercentage?: number
  durationMinutes: number
}

export function fetchServices() {
  return api.get<SalonService[]>('/servicos').then((response) => response.data)
}

export function createService(input: SalonServiceInput) {
  return api.post<SalonService>('/servicos', input).then((response) => response.data)
}

export function updateService(id: number, input: SalonServiceInput) {
  return api.put<SalonService>(`/servicos/${id}`, input).then((response) => response.data)
}

export function deactivateService(id: number) {
  return api.delete<void>(`/servicos/${id}`).then((response) => response.data)
}
