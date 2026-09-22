import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import axios from 'axios'
import type { UserRole } from '@/types/auth'

const allowedPathsByRole: Record<UserRole, (path: string) => boolean> = {
  ADMIN: (path) => path === '/' || ['/funcionarios', '/servicos', '/estabelecimento'].includes(path),
  CLIENT: (path) => path === '/agendamentos' || /^\/estabelecimentos\/\d+\/agendar$/.test(path),
  PROFESSIONAL: (path) => path === '/minha-agenda',
}

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const emailRef = useRef<HTMLInputElement>(null)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // RNF.01.01: foca automaticamente o campo de e-mail ao carregar a tela.
  useEffect(() => {
    emailRef.current?.focus()
  }, [])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const user = await login({ email, password })
      const requestedPath = (location.state as { from?: Location })?.from?.pathname
      const defaultPath = user.role === 'CLIENT'
        ? '/agendamentos'
        : user.role === 'PROFESSIONAL' ? '/minha-agenda' : '/'
      const redirectTo = requestedPath && allowedPathsByRole[user.role](requestedPath)
        ? requestedPath
        : defaultPath
      navigate(redirectTo, { replace: true })
    } catch (err) {
      const message = axios.isAxiosError(err)
        ? (err.response?.data as { message?: string })?.message
        : undefined
      setError(message ?? 'E-mail ou senha inválidos')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background-100 px-4 py-10">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-3xl border border-background-300 bg-background-50 p-8 shadow-sm"
      >
        <h1 className="font-[var(--font-display)] text-2xl text-text-900">
          Entrar no SalonHub
        </h1>
        <p className="mt-1 text-sm text-text-600">Acesse com seu e-mail e senha.</p>

        {(location.state as { message?: string } | null)?.message && (
          <p className="mt-4 rounded-xl bg-success-50 px-3 py-2 text-sm text-success-600">
            {(location.state as { message: string }).message}
          </p>
        )}

        <label className="mt-6 block text-sm font-medium text-text-800" htmlFor="email">
          E-mail
        </label>
        <input
          ref={emailRef}
          id="email"
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="mt-1 w-full rounded-lg border border-background-300 px-3 py-2 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
        />

        <label className="mt-4 block text-sm font-medium text-text-800" htmlFor="password">
          Senha
        </label>
        <input
          id="password"
          type="password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mt-1 w-full rounded-lg border border-background-300 px-3 py-2 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
        />

        {error && (
          <p role="alert" className="mt-3 text-sm text-danger-600">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={isSubmitting}
          className="mt-6 w-full rounded-lg bg-primary-600 py-2.5 font-medium text-on-primary transition hover:bg-primary-700 disabled:opacity-60"
        >
          {isSubmitting ? 'Entrando...' : 'Entrar'}
        </button>

        <div className="mt-6 border-t border-background-300 pt-5 text-center text-sm text-text-600">
          <p>Novo por aqui?</p>
          <div className="mt-2 flex justify-center gap-4">
            <Link className="font-semibold text-text-700 hover:underline" to="/cadastro-cliente">Sou cliente</Link>
            <Link className="font-semibold text-text-700 hover:underline" to="/cadastro">Tenho um estabelecimento</Link>
          </div>
        </div>
      </form>
    </div>
  )
}
