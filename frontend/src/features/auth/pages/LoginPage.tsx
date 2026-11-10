/** Autentica o usuário e encaminha a sessão ao destino adequado. */
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ArrowRight, Eye, EyeOff, LockKeyhole, Mail } from 'lucide-react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import { AuthShell } from '@/features/auth/components/AuthShell'
import { useAuth } from '@/hooks/useAuth'
import { getApiErrorMessage } from '@/lib/apiError'
import { postLoginPath } from '@/features/auth/postLoginPath'
import { REDIRECT_PARAM, SESSION_ENDED_PARAM, SESSION_ENDED_VALUE } from '@/lib/session'

/** Exibe o formulário e informa quando a sessão anterior expirou ou foi encerrada. */
export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const sessionEnded = searchParams.get(SESSION_ENDED_PARAM) === SESSION_ENDED_VALUE
  const emailRef = useRef<HTMLInputElement>(null)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    emailRef.current?.focus()
  }, [])

  /** Retoma a rota solicitada antes do login quando ela é compatível com o papel autenticado. */
  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const user = await login({ email, password })
      const requestedPath = (location.state as { from?: Location })?.from?.pathname
        ?? searchParams.get(REDIRECT_PARAM)
        ?? undefined
      navigate(postLoginPath(user.role, requestedPath), { replace: true })
    } catch (err) {
      setError(axios.isAxiosError(err) && err.response?.status === 401
        ? getApiErrorMessage(err, 'E-mail ou senha inválidos')
        : getApiErrorMessage(err, 'Não foi possível entrar. Verifique se o backend está em execução e tente novamente.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthShell
      eyebrow="Bem-vindo de volta"
      title="Entre na sua conta"
      subtitle="Acesse o painel do seu estabelecimento, sua agenda profissional ou seus agendamentos como cliente."
      maxWidth="sm"
    >
      {sessionEnded && !(location.state as { message?: string } | null)?.message && (
        <p role="status" className="mb-5 rounded-xl border border-warning-500/20 bg-warning-50 px-4 py-3 text-sm font-medium text-warning-700">
          Sua sessão expirou ou foi encerrada. Entre novamente para continuar de onde parou.
        </p>
      )}
      {(location.state as { message?: string } | null)?.message && (
        <p className="mb-5 rounded-xl border border-success-500/20 bg-success-50 px-4 py-3 text-sm font-medium text-success-700">
          {(location.state as { message: string }).message}
        </p>
      )}

      <form onSubmit={handleSubmit}>
        <label className="block text-sm font-semibold text-text-800" htmlFor="email">
          E-mail
        </label>
        <div className="relative">
          <Mail className="pointer-events-none absolute left-3.5 top-1/2 mt-1 size-[17px] -translate-y-1/2 text-text-400" />
          <input
            ref={emailRef}
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="voce@exemplo.com"
            className="auth-input pl-10"
          />
        </div>

        <div className="mt-5 flex items-center justify-between">
          <label className="text-sm font-semibold text-text-800" htmlFor="password">Senha</label>
          <Link to="/recuperar-senha" state={{ email }} className="text-sm font-semibold text-primary-700 hover:text-primary-800">
            Esqueci minha senha
          </Link>
        </div>
        <div className="relative">
          <LockKeyhole className="pointer-events-none absolute left-3.5 top-1/2 mt-1 size-[17px] -translate-y-1/2 text-text-400" />
          <input
            id="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Sua senha"
            className="auth-input px-10"
          />
          <button
            type="button"
            onClick={() => setShowPassword((current) => !current)}
            className="absolute right-2.5 top-1/2 mt-1 grid size-8 -translate-y-1/2 place-items-center rounded-lg text-text-400 hover:bg-background-200 hover:text-text-700"
            aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
            title={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
          >
            {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
          </button>
        </div>

        {error && (
          <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-3.5 py-2.5 text-sm text-danger-600">
            {error}
          </p>
        )}

        <button type="submit" disabled={isSubmitting} className="primary-button mt-6 w-full py-3">
          {isSubmitting ? 'Entrando...' : 'Entrar'}
          {!isSubmitting && <ArrowRight size={17} />}
        </button>
      </form>

      <div className="mt-7 border-t border-background-300 pt-6">
        <p className="text-center text-xs font-bold uppercase tracking-[0.13em] text-text-400">Primeiro acesso ao Meu Espaço?</p>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <Link to="/cadastro-cliente" className="secondary-button py-3">Sou cliente</Link>
          <Link to="/cadastro" className="secondary-button py-3">Tenho um negócio</Link>
        </div>
      </div>
    </AuthShell>
  )
}
