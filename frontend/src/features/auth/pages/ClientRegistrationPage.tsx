import { useState, type FormEvent } from 'react'
import { ArrowRight, Mail, UserRound } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthShell } from '@/features/auth/components/AuthShell'
import { registerClient } from '@/features/auth/services/authApi'
import { getApiErrorMessage } from '@/lib/apiError'

export function ClientRegistrationPage() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    const form = new FormData(event.currentTarget)
    try {
      await registerClient({
        name: String(form.get('name')),
        email: String(form.get('email')),
        password: String(form.get('password')),
      })
      navigate('/login', { replace: true, state: { message: 'Conta criada. Entre para agendar.' } })
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Não foi possível criar sua conta.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthShell
      eyebrow="Conta de cliente"
      title="Crie sua conta"
      subtitle="Use um único acesso para marcar horários e acompanhar seus agendamentos."
      maxWidth="sm"
    >
      <form onSubmit={handleSubmit} className="space-y-5">
        <ClientField name="name" label="Nome completo" autoComplete="name" placeholder="Seu nome" icon={<UserRound size={17} />} />
        <ClientField name="email" label="E-mail" type="email" autoComplete="email" placeholder="voce@exemplo.com" icon={<Mail size={17} />} />
        <ClientField name="password" label="Senha" type="password" minLength={8} autoComplete="new-password" placeholder="Mínimo de 8 caracteres" />

        {error && <p role="alert" className="rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{error}</p>}

        <button disabled={isSubmitting} className="primary-button w-full py-3">
          {isSubmitting ? 'Criando conta...' : 'Criar conta'}
          {!isSubmitting && <ArrowRight size={17} />}
        </button>
      </form>

      <p className="mt-6 border-t border-background-300 pt-5 text-center text-sm text-text-600">
        Já possui conta? <Link className="font-semibold text-primary-700 hover:underline" to="/login">Entrar</Link>
      </p>
    </AuthShell>
  )
}

function ClientField({ label, icon, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string; name: string; icon?: React.ReactNode }) {
  return (
    <label className="block text-sm font-semibold text-text-800">
      {label}
      <span className="relative block">
        {icon && <span className="pointer-events-none absolute left-3.5 top-1/2 mt-1 -translate-y-1/2 text-text-400">{icon}</span>}
        <input required {...props} className={`auth-input ${icon ? 'pl-10' : ''}`} />
      </span>
    </label>
  )
}
