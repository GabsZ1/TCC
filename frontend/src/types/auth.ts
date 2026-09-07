export type UserRole = 'ADMIN' | 'PROFESSIONAL'

export interface AuthenticatedUser {
  id: number
  name: string
  email: string
  role: UserRole
  establishmentId: number
  establishmentName: string
}

export interface LoginCredentials {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  type: string
  expiresAt: string
  user: AuthenticatedUser
}