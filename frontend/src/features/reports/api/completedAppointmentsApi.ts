/** Consulta e exporta os atendimentos concluídos do estabelecimento. */
import { api } from '@/lib/api'

export interface CompletedAppointmentEntry {
  appointmentId: number
  completedAt: string
  professionalId: number
  professionalName: string
  serviceId: number
  serviceName: string
  clientName: string
  servicePrice: number
  commissionPercentage: number
  commissionAmount: number
}

export interface CompletedAppointmentsPage {
  startDate: string | null
  endDate: string | null
  professionalId: number | null
  entries: CompletedAppointmentEntry[]
  page: number
  size: number
  totalPages: number
  totalElements: number
  totalServiceValue: number
  totalCommission: number
}

export interface CompletedAppointmentsFilters {
  startDate?: string
  endDate?: string
  professionalId?: number
}

export const COMPLETED_APPOINTMENTS_PAGE_SIZE = 20

export function fetchCompletedAppointments(filters: CompletedAppointmentsFilters, page: number) {
  return api.get<CompletedAppointmentsPage>('/estabelecimentos/me/atendimentos-realizados', {
    params: { ...filters, page, size: COMPLETED_APPOINTMENTS_PAGE_SIZE },
  }).then((response) => response.data)
}
