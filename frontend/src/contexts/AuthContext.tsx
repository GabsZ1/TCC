import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api, clearStoredToken, getStoredToken, setStoredToken } from '@/lib/api'
import type { AuthenticatedUser, LoginCredentials, LoginResponse } from '@/types/auth'
import { AuthContext } from '@/contexts/authStore'

// RNF.05.01 (User Story Logout): expira a sessão após 30 min de inatividade.
const INACTIVITY_LIMIT_MS = 30 * 60 * 1000

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [isLoading, setIsLoading] = useState(() => !!getStoredToken())
  const sessionRequestRef = useRef<AbortController | null>(null)

  const logout = useCallback(() => {
    clearStoredToken()
    setUser(null)
  }, [])

  const login = useCallback(async (credentials: LoginCredentials) => {
    sessionRequestRef.current?.abort()
    const { data } = await api.post<LoginResponse>('/auth/login', credentials)
    setStoredToken(data.token)
    setUser(data.user)
    setIsLoading(false)
    return data.user
  }, [])

  const refreshUser = useCallback(async () => {
    const { data } = await api.get<AuthenticatedUser>('/auth/me')
    setUser(data)
  }, [])

  // Carrega o usuário autenticado a partir do token salvo, se houver.
  useEffect(() => {
    const token = getStoredToken()
    if (!token) return

    const controller = new AbortController()
    sessionRequestRef.current = controller

    api
      .get<AuthenticatedUser>('/auth/me', { signal: controller.signal })
      .then(({ data }) => {
        if (!controller.signal.aborted && getStoredToken() === token) setUser(data)
      })
      .catch(() => {
        if (!controller.signal.aborted && getStoredToken() === token) clearStoredToken()
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false)
      })

    return () => controller.abort()
  }, [])

  // Encerra a sessão automaticamente após inatividade prolongada.
  useEffect(() => {
    if (!user) return

    let timeoutId: ReturnType<typeof setTimeout>

    const resetTimer = () => {
      clearTimeout(timeoutId)
      timeoutId = setTimeout(logout, INACTIVITY_LIMIT_MS)
    }

    const activityEvents = ['mousedown', 'keydown', 'scroll', 'touchstart']
    activityEvents.forEach((event) => window.addEventListener(event, resetTimer))
    resetTimer()

    return () => {
      clearTimeout(timeoutId)
      activityEvents.forEach((event) => window.removeEventListener(event, resetTimer))
    }
  }, [user, logout])

  const value = useMemo(
    () => ({ user, isAuthenticated: !!user, isLoading, login, logout, refreshUser }),
    [user, isLoading, login, logout, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
