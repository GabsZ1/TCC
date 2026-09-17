import { api } from '@/lib/api'

export interface DashboardSummary {
  todaysCompletedServices: number
  monthlyCommissionTotal: number
  activeEmployees: number
}

export function fetchDashboardSummary() {
  return api.get<DashboardSummary>('/estabelecimentos/me/dashboard').then((response) => response.data)
}
