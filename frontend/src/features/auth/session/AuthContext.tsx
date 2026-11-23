/** Disponibiliza a sessão autenticada e as ações de entrada e saída para a árvore de componentes. */
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, clearStoredToken, getStoredToken, setStoredToken } from '@/lib/api'
import type { AuthenticatedUser, LoginCredentials, LoginResponse } from '@/features/auth/session/types'
import { AuthContext } from '@/features/auth/session/authStore'
import { shouldRenewToken } from '@/lib/session'

// RNF.05.01 (User Story Logout): expira a sessão após 30 min de inatividade.
const INACTIVITY_LIMIT_MS = 30 * 60 * 1000
// Intervalo mínimo entre tentativas de renovar o token (evita repetir em caso de falha de rede).
const RENEW_RETRY_MS = 60 * 1000

/** Carrega a sessão, expõe entrada e saída e mantém o restante da interface sincronizado. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [isLoading, setIsLoading] = useState(() => !!getStoredToken())
  const sessionRequestRef = useRef<AbortController | null>(null)
  const lastRenewAttemptRef = useRef(0)

  const logout = useCallback(() => {
    sessionRequestRef.current?.abort()
    clearStoredToken()
    void queryClient.cancelQueries()
    queryClient.clear()
    setUser(null)
    setIsLoading(false)
  }, [queryClient])

  const login = useCallback(async (credentials: LoginCredentials) => {
    sessionRequestRef.current?.abort()
    const { data } = await api.post<LoginResponse>('/auth/login', credentials)
    void queryClient.cancelQueries()
    queryClient.clear()
    setStoredToken(data.token)
    setUser(data.user)
    setIsLoading(false)
    return data.user
  }, [queryClient])

  // Quem está usando o sistema não é desconectado quando o token expira: perto do fim da
  // validade, qualquer atividade renova o token. Inatividade continua encerrando a sessão.
  const renewSessionIfNeeded = useCallback(() => {
    const token = getStoredToken()
    const now = Date.now()
    if (!token || !shouldRenewToken(token, now) || now - lastRenewAttemptRef.current < RENEW_RETRY_MS) return
    lastRenewAttemptRef.current = now
    api
      .post<LoginResponse>('/auth/renovar')
      .then(({ data }) => {
        if (getStoredToken() === token) setStoredToken(data.token)
      })
      .catch(() => {
        // 401 já é tratado pelo interceptor do api.ts; outros erros: nova tentativa na próxima atividade.
      })
  }, [])

  const refreshUser = useCallback(async () => {
    const token = getStoredToken()
    const { data } = await api.get<AuthenticatedUser>('/auth/me')
    if (token && getStoredToken() === token) setUser(data)
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
      renewSessionIfNeeded()
    }

    const activityEvents = ['mousedown', 'keydown', 'scroll', 'touchstart']
    activityEvents.forEach((event) => window.addEventListener(event, resetTimer))
    resetTimer()

    return () => {
      clearTimeout(timeoutId)
      activityEvents.forEach((event) => window.removeEventListener(event, resetTimer))
    }
  }, [user, logout, renewSessionIfNeeded])

  const value = useMemo(
    () => ({ user, isAuthenticated: !!user, isLoading, login, logout, refreshUser }),
    [user, isLoading, login, logout, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
