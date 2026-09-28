import { api } from '@/lib/api'
import type { UserRole } from '@/types/auth'
import type { SalonService } from '@/features/services/servicesApi'

export interface Employee {
  id: number
  name: string
  email: string
  role: UserRole
  establishmentId: number | null
  establishmentName: string | null
  active: boolean
  passwordInitialized: boolean
  occupation: string | null
  cpf: string | null
  phone: string | null
  instagram: string | null
  address: string | null
  hireDate: string | null
  hasPhoto: boolean
}

/** Campos enviados no cadastro e na edição (US 2.1 / 2.2). */
export interface EmployeeInput {
  name: string
  email: string
  occupation: string
  cpf: string
  phone?: string
  instagram?: string
  address?: string
  hireDate?: string
}

export type EmployeeCreateInput = EmployeeInput
export type EmployeeUpdateInput = EmployeeInput

export interface EmployeeCreationResult {
  id: number
  name: string
  email: string
  active: boolean
  firstAccessToken: string
  firstAccessExpiresAt: string
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

/** Foto do funcionário (JPG, PNG ou WEBP de até 2 MB). */
export function uploadEmployeePhoto(id: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return api.put<void>(`/funcionarios/${id}/foto`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function deleteEmployeePhoto(id: number) {
  return api.delete<void>(`/funcionarios/${id}/foto`)
}

export function fetchEmployeePhoto(id: number) {
  return api.get<Blob>(`/funcionarios/${id}/foto`, { responseType: 'blob' }).then((res) => res.data)
}
