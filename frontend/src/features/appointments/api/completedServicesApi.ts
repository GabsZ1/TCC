/** Consulta os atendimentos concluídos e os dados exibidos ao profissional. */
import { api } from '@/lib/api'

export interface CompletedServiceEntry {
  appointmentId: number
  completedAt: string
  serviceId: number
  serviceName: string
  clientName: string
  servicePrice: number
  commissionPercentage: number
  commissionAmount: number
}

export interface CompletedServicesSummary {
  totalServices: number
  totalServiceValue: number
  totalCommission: number
}

export interface ServiceOption {
  id: number
  name: string
}

export interface ProfessionalCompletedServices {
  startDate: string
  endDate: string
  serviceId: number | null
  entries: CompletedServiceEntry[]
  summary: CompletedServicesSummary
  availableServices: ServiceOption[]
  page: number
  totalPages: number
  totalElements: number
}

export interface CompletedServicesFilters {
  startDate?: string
  endDate?: string
  serviceId?: number
  page?: number
}

export function fetchMyCompletedServices(filters: CompletedServicesFilters) {
  return api.get<ProfessionalCompletedServices>('/profissionais/me/atendimentos-realizados', { params: { ...filters, size: 20 } })
    .then((response) => response.data)
}
