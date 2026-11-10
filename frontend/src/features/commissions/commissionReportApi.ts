/** Busca os valores de comissão do período e os arquivos de exportação. */
import { api } from '@/lib/api'

export interface CommissionReportEntry {
  professionalId: number
  professionalName: string
  completedServicesCount: number
  totalServiceValue: number
  averageCommissionPercentage: number
  totalCommission: number
}

export interface CommissionReport {
  periodStart: string
  periodEnd: string
  entries: CommissionReportEntry[]
  totalCommission: number
}

export interface CommissionReportFilters {
  startDate?: string
  endDate?: string
  professionalId?: number
}

export function fetchCommissionReport(filters: CommissionReportFilters) {
  return api.get<CommissionReport>('/estabelecimentos/me/relatorio-comissoes', { params: filters })
    .then((response) => response.data)
}