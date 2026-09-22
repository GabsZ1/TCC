import { useState, type FormEvent } from 'react'
import { ArrowRight, CheckCircle2, KeyRound } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthShell } from '@/features/auth/components/AuthShell'
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
    <AuthShell
      eyebrow="Primeiro acesso"
      title={completed ? 'Tudo pronto.' : 'Defina sua senha'}
      subtitle={completed ? 'Sua senha foi criada e seu acesso já pode ser usado.' : 'Crie uma senha segura para ativar seu acesso ao SalonHub.'}
      maxWidth="sm"
    >
      {completed ? (
        <div className="text-center">
          <span className="mx-auto grid size-14 place-items-center rounded-2xl bg-success-50 text-success-600">
            <CheckCircle2 size={28} />
          </span>
          <p className="mt-5 text-sm leading-6 text-text-600">Senha definida com sucesso. Entre com seu e-mail e a nova senha.</p>
          <Link className="primary-button mt-6 w-full py-3" to="/login">Ir para o login <ArrowRight size={17} /></Link>
        </div>
      ) : (
        <form onSubmit={submit}>
          <div className="mb-5 flex items-center gap-3 rounded-xl bg-primary-50 px-4 py-3 text-sm text-text-700">
            <KeyRound size={18} className="shrink-0 text-primary-700" />
            Use pelo menos 8 caracteres e evite senhas já utilizadas em outros serviços.
          </div>
          <label className="block text-sm font-semibold text-text-800" htmlFor="first-password">Nova senha</label>
          <input id="first-password" type="password" minLength={8} autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} className="auth-input" />
          <label className="mt-5 block text-sm font-semibold text-text-800" htmlFor="first-password-confirmation">Confirmar senha</label>
          <input id="first-password-confirmation" type="password" minLength={8} autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} className="auth-input" />
          {error && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{error}</p>}
          <button disabled={submitting} className="primary-button mt-6 w-full py-3">{submitting ? 'Salvando...' : 'Definir senha'} {!submitting && <ArrowRight size={17} />}</button>
        </form>
      )}
    </AuthShell>
  )
}
