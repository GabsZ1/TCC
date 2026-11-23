/** Centraliza as requisições de autenticação, cadastro e recuperação de senha. */
import { api } from '@/lib/api'
import type { AuthenticatedUser, LoginResponse } from '@/features/auth/session/types'

export interface InitialRegistrationInput {
  ownerName: string
  ownerEmail: string
  password: string
  establishmentName: string
  cnpj: string
  establishmentEmail?: string
  establishmentPhone?: string
}

export interface InitialRegistrationResponse {
  establishmentId: number
  establishmentName: string
  userId: number
  ownerName: string
  ownerEmail: string
}

export interface ClientRegistrationInput {
  name: string
  email: string
  phone: string
  password: string
  passwordConfirmation: string
}

export function registerInitial(input: InitialRegistrationInput) {
  return api.post<InitialRegistrationResponse>('/auth/cadastro', input).then((response) => response.data)
}

export function registerClient(input: ClientRegistrationInput) {
  return api.post<AuthenticatedUser>('/auth/clientes', input).then((response) => response.data)
}

export function completeFirstAccess(token: string, password: string) {
  return api.post<void>('/auth/primeiro-acesso', { token, password })
}

export interface PasswordChangeInput {
  currentPassword: string
  newPassword: string
  newPasswordConfirmation: string
}

/** Troca a senha; a resposta traz um token novo, porque a troca encerra todas as sessões. */
export function changePassword(input: PasswordChangeInput) {
  return api.patch<LoginResponse>('/auth/senha', input).then((res) => res.data)
}

export interface PasswordRecoveryResponse {
  message: string
  expiresInMinutes: number
}

export function requestPasswordReset(email: string) {
  return api.post<PasswordRecoveryResponse>('/auth/recuperar-senha', { email }).then((response) => response.data)
}

export function validatePasswordResetToken(token: string) {
  return api.get<void>('/auth/redefinir-senha', { params: { token } }).then(() => true)
}

export interface PasswordResetInput {
  token: string
  newPassword: string
  newPasswordConfirmation: string
}

export function resetPassword(input: PasswordResetInput) {
  return api.post<void>('/auth/redefinir-senha', input)
}
