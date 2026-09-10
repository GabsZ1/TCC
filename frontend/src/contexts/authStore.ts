import { createContext } from 'react'
import type { AuthenticatedUser, LoginCredentials } from '@/types/auth'

export interface AuthContextValue {
  user: AuthenticatedUser | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (credentials: LoginCredentials) => Promise<AuthenticatedUser>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
