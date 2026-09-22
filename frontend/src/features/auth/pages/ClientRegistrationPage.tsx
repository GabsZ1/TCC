import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
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
    <main className="flex min-h-screen items-center justify-center bg-background-100 px-4 py-10">
      <section className="w-full max-w-md rounded-3xl border border-background-300 bg-background-50 p-7 shadow-sm sm:p-9">
        <Link to="/login" className="font-[var(--font-display)] text-xl text-text-900">SalonHub</Link>
        <h1 className="mt-8 font-[var(--font-display)] text-3xl text-text-900">Crie sua conta</h1>
        <p className="mt-2 text-base text-text-600">Use sua conta para marcar e acompanhar seus horários.</p>
        <form onSubmit={handleSubmit} className="mt-7 space-y-4">
          <ClientField name="name" label="Nome completo" autoComplete="name" />
          <ClientField name="email" label="E-mail" type="email" autoComplete="email" />
          <ClientField name="password" label="Senha" type="password" minLength={8} autoComplete="new-password" />
          {error && <p role="alert" className="text-sm text-danger-600">{error}</p>}
          <button disabled={isSubmitting} className="w-full rounded-xl bg-primary-600 px-5 py-3 font-semibold text-on-primary hover:bg-primary-700 disabled:opacity-60">
            {isSubmitting ? 'Criando conta...' : 'Criar conta'}
          </button>
        </form>
        <p className="mt-6 text-center text-sm text-text-600">Já possui conta? <Link className="font-semibold text-text-700 hover:underline" to="/login">Entrar</Link></p>
      </section>
    </main>
  )
}

function ClientField({ label, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string; name: string }) {
  return (
    <label className="block text-sm font-medium text-text-800">
      {label}
      <input required {...props} className="mt-1.5 w-full rounded-xl border border-background-300 px-3.5 py-2.5 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100" />
    </label>
  )
}
