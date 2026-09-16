import { api } from '@/lib/api'
import type { AuthenticatedUser } from '@/types/auth'
import type { SalonService } from '@/features/services/servicesApi'

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

export interface ProfessionalServices {
  professionalId: number
  allServices: boolean
  services: SalonService[]
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

export function reactivateEmployee(id: number) {
  return api.patch<Employee>(`/funcionarios/${id}/ativacao`).then((res) => res.data)
}

export function fetchEmployeeServices(id: number) {
  return api.get<ProfessionalServices>(`/funcionarios/${id}/servicos`).then((res) => res.data)
}

export function updateEmployeeServices(id: number, allServices: boolean, serviceIds: number[]) {
  return api.put<ProfessionalServices>(`/funcionarios/${id}/servicos`, { allServices, serviceIds }).then((res) => res.data)
}

export function fetchMyProfessionalServices() {
  return api.get<ProfessionalServices>('/funcionarios/me/servicos').then((res) => res.data)
}

/**
 * Remove um funcionário. Se ele não tiver atendimentos vinculados, é
 * removido permanentemente; caso contrário, o back-end apenas o desativa
 * (RN 2.3.2 do TCC) — o resultado informa qual das duas coisas aconteceu.
 */
export function removeEmployee(id: number) {
  return api.delete<EmployeeRemovalResult>(`/funcionarios/${id}`).then((res) => res.data)
}
