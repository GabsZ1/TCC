import { api } from '@/lib/api'
import type { AuthenticatedUser } from '@/types/auth'

export type Employee = AuthenticatedUser

export interface EmployeeCreateInput {
  name: string
  email: string
  password: string
}

export interface EmployeeUpdateInput {
  name: string
  email: string
}

export interface EmployeeRemovalResult {
  removedPermanently: boolean
  message: string
}

export function fetchEmployees() {
  return api.get<Employee[]>('/funcionarios').then((res) => res.data)
}

export function createEmployee(input: EmployeeCreateInput) {
  return api.post<Employee>('/funcionarios', input).then((res) => res.data)
}

export function updateEmployee(id: number, input: EmployeeUpdateInput) {
  return api.put<Employee>(`/funcionarios/${id}`, input).then((res) => res.data)
}

/**
 * Remove um funcionário. Se ele não tiver atendimentos vinculados, é
 * removido permanentemente; caso contrário, o back-end apenas o desativa
 * (RN 2.3.2 do TCC) — o resultado informa qual das duas coisas aconteceu.
 */
export function removeEmployee(id: number) {
  return api.delete<EmployeeRemovalResult>(`/funcionarios/${id}`).then((res) => res.data)
}
