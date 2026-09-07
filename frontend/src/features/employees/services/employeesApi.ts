import { api } from '@/lib/api'
import type { AuthenticatedUser } from '@/types/auth'

export function fetchEmployees() {
  return api.get<AuthenticatedUser[]>('/funcionarios').then((res) => res.data)
}