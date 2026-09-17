import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { completeFirstAccess } from '@/features/auth/services/authApi'
import { getApiErrorMessage } from '@/lib/apiError'

export function FirstAccessPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [completed, setCompleted] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!token) return setError('Link de primeiro acesso inválido.')
    if (password.length < 8) return setError('A senha deve ter no mínimo 8 caracteres.')
    if (password !== confirmation) return setError('As senhas não coincidem.')
    setSubmitting(true)
    setError(null)
    try {
      await completeFirstAccess(token, password)
      setCompleted(true)
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Não foi possível concluir o primeiro acesso.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-sand-50 p-4">
      <section className="w-full max-w-md rounded-2xl bg-white p-6 shadow-sm">
        <h1 className="font-[var(--font-display)] text-2xl text-brand-900">Primeiro acesso</h1>
        {completed ? (
          <div className="mt-4">
            <p className="text-sage-600">Senha definida com sucesso.</p>
            <Link className="mt-4 inline-block text-brand-600 underline" to="/login">Entrar</Link>
          </div>
        ) : (
          <form className="mt-4" onSubmit={submit}>
            <label className="block text-sm font-medium text-brand-800" htmlFor="first-password">Nova senha</label>
            <input id="first-password" type="password" minLength={8} autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} className="mt-1 w-full rounded-lg border border-brand-200 px-3 py-2" />
            <label className="mt-4 block text-sm font-medium text-brand-800" htmlFor="first-password-confirmation">Confirmar senha</label>
            <input id="first-password-confirmation" type="password" minLength={8} autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} className="mt-1 w-full rounded-lg border border-brand-200 px-3 py-2" />
            {error && <p role="alert" className="mt-4 text-sm text-danger-600">{error}</p>}
            <button disabled={submitting} className="mt-6 w-full rounded-lg bg-brand-600 px-4 py-2 font-medium text-white disabled:opacity-60">{submitting ? 'Salvando...' : 'Definir senha'}</button>
          </form>
        )}
      </section>
    </main>
  )
}
