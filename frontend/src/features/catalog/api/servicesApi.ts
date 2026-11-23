/** Centraliza as operações HTTP do catálogo de serviços. */
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

export interface Page<T> {
  content: T[]
  number: number
  totalPages: number
  totalElements: number
}

export interface ServiceCounts {
  total: number
  active: number
  inactive: number
}

export function fetchServices() {
  return fetchAllAdministrativeServices().then((services) => services.filter((service) => service.active))
}

export function fetchAdministrativeServices(page = 0, size = 20, search = '', active?: boolean) {
  return api.get<Page<SalonService>>('/servicos', { params: { page, size, search: search || undefined, active } }).then((response) => response.data)
}

export function fetchServiceCounts() {
  return api.get<ServiceCounts>('/servicos/resumo').then((response) => response.data)
}

export async function fetchAllAdministrativeServices() {
  const firstPage = await fetchAdministrativeServices()
  const pages = await Promise.all(
    Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) => fetchAdministrativeServices(index + 1)),
  )
  return [firstPage, ...pages].flatMap((page) => page.content)
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

export function reactivateService(id: number) {
  return api.patch<SalonService>(`/servicos/${id}/ativacao`).then((response) => response.data)
}
