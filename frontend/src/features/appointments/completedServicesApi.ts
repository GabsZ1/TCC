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
}

export interface CompletedServicesFilters {
  startDate?: string
  endDate?: string
  serviceId?: number
}

export function fetchMyCompletedServices(filters: CompletedServicesFilters) {
  return api.get<ProfessionalCompletedServices>('/profissionais/me/atendimentos-realizados', { params: filters })
    .then((response) => response.data)
}
