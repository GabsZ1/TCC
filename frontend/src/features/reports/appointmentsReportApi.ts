import { api } from '@/lib/api'

export interface AppointmentsReportEntry {
  professionalId: number
  professionalName: string
  confirmedCount: number
  completedCount: number
  cancelledCount: number
  invalidatedCount: number
  noShowCount: number
  totalCount: number
  /** Taxas sobre os desfechos (concluído, cancelado, falta); invalidados não entram. */
  completionRate: number
  attendanceRate: number
  cancellationRate: number
  noShowRate: number
}

export interface AppointmentsReport {
  periodStart: string
  periodEnd: string
  entries: AppointmentsReportEntry[]
  totalConfirmed: number
  totalCompleted: number
  totalCancelled: number
  totalInvalidated: number
  totalNoShows: number
  completionRate: number
  attendanceRate: number
  cancellationRate: number
  noShowRate: number
}

export interface AppointmentsReportFilters {
  startDate?: string
  endDate?: string
  professionalId?: number
}

export function fetchAppointmentsReport(filters: AppointmentsReportFilters) {
  return api.get<AppointmentsReport>('/estabelecimentos/me/relatorio-atendimentos', { params: filters })
    .then((response) => response.data)
}
