import { api } from '@/lib/api'

export interface FinancialReportLine {
  id: number
  name: string
  completedCount: number
  revenue: number
  commission: number
  averageTicket: number
}

export interface FinancialReport {
  periodStart: string
  periodEnd: string
  completedCount: number
  grossRevenue: number
  totalCommission: number
  revenueAfterCommission: number
  averageTicket: number
  byService: FinancialReportLine[]
  byProfessional: FinancialReportLine[]
}

export interface FinancialReportFilters {
  startDate?: string
  endDate?: string
  professionalId?: number
  serviceId?: number
}

export const FINANCIAL_REPORT_CSV_PATH = '/estabelecimentos/me/relatorio-financeiro/csv'

export function fetchFinancialReport(filters: FinancialReportFilters) {
  return api.get<FinancialReport>('/estabelecimentos/me/relatorio-financeiro', { params: filters }).then((response) => response.data)
}
