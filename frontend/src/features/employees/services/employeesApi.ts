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

export function fetchEmployees() {
  return api.get<Employee[]>('/funcionarios').then((res) => res.data)
}

export function createEmployee(input: EmployeeCreateInput) {
  return api.post<Employee>('/funcionarios', input).then((res) => res.data)
}

export function updateEmployee(id: number, input: EmployeeUpdateInput) {
  return api.put<Employee>(`/funcionarios/${id}`, input).then((res) => res.data)
}

export function deactivateEmployee(id: number) {
  return api.delete<void>(`/funcionarios/${id}`).then((res) => res.data)
}
