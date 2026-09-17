import { api } from '@/lib/api'
import type { AuthenticatedUser } from '@/types/auth'
import type { SalonService } from '@/features/services/servicesApi'

export type Employee = AuthenticatedUser

export interface EmployeeCreateInput {
  name: string
  email: string
}

export interface EmployeeCreationResult extends Employee {
  firstAccessToken: string
  firstAccessExpiresAt: string
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
  return api.post<EmployeeCreationResult>('/funcionarios', input).then((res) => res.data)
}

export function updateEmployee(id: number, input: EmployeeUpdateInput) {
  return api.put<Employee>(`/funcionarios/${id}`, input).then((res) => res.data)
}

export function deactivateEmployee(id: number) {
  return api.patch<Employee>(`/funcionarios/${id}/desativacao`).then((res) => res.data)
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

/** Exclui definitivamente; o backend rejeita a operação se houver vínculos. */
export function removeEmployee(id: number) {
  return api.delete<EmployeeRemovalResult>(`/funcionarios/${id}`).then((res) => res.data)
}
