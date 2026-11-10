/** Busca os indicadores usados no painel do estabelecimento. */
import { api } from '@/lib/api'

export interface DashboardSummary {
  todaysCompletedServices: number
  monthlyCommissionTotal: number
  activeEmployees: number
  todaysAppointments: number
  pendingClosure: number
  todaysRevenue: number
  upcomingAppointments: Array<{ id: number; startsAt: string; customerName: string; serviceName: string; professionalName: string }>
}

export function fetchDashboardSummary() {
  return api.get<DashboardSummary>('/estabelecimentos/me/dashboard').then((response) => response.data)
}
