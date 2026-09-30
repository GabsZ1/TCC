import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeft, MailCheck, Mail } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { AuthShell } from '@/features/auth/components/AuthShell'
import { requestPasswordReset } from '@/features/auth/services/authApi'
import { getApiErrorMessage } from '@/lib/apiError'

/**
 * US 2.20 — Recuperar senha (pedido do link).
 * RNF.01.01: o botão fica cinza e desabilitado enquanto o e-mail é enviado.
 */
export function ForgotPasswordPage() {
  const location = useLocation()
  const [email, setEmail] = useState((location.state as { email?: string } | null)?.email ?? '')
  const mutation = useMutation({ mutationFn: requestPasswordReset })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!email.trim() || mutation.isPending) return
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) || email.trim().length > 150) return
    mutation.mutate(email.trim())
  }

  return (
    <AuthShell
      eyebrow="Recuperar acesso"
      title={mutation.isSuccess ? 'Solicitação recebida' : 'Esqueceu sua senha?'}
      subtitle={mutation.isSuccess
        ? 'Caso receba a mensagem, confira também a caixa de spam.'
        : 'Informe o e-mail da sua conta e enviaremos um link para você criar uma nova senha.'}
      maxWidth="sm"
    >
      {mutation.isSuccess ? (
        <div className="text-center">
          <span className="mx-auto grid size-14 place-items-center rounded-2xl bg-success-50 text-success-600">
            <MailCheck size={28} />
          </span>
          <p role="status" className="mt-5 text-sm leading-6 text-text-700">{mutation.data.message}</p>

          <Link className="secondary-button mt-6 w-full py-3" to="/login"><ArrowLeft size={17} /> Voltar para o login</Link>
        </div>
      ) : (
        <form onSubmit={submit}>
          <label className="block text-sm font-semibold text-text-800" htmlFor="recovery-email">E-mail</label>
          <div className="relative">
            <Mail className="pointer-events-none absolute left-3.5 top-1/2 mt-1 size-[17px] -translate-y-1/2 text-text-400" />
            <input
              id="recovery-email"
              type="email"
              autoComplete="email"
              autoFocus
              required
              maxLength={150}
              value={email}
              onChange={(event) => { setEmail(event.target.value); if (mutation.isError) mutation.reset() }}
              placeholder="voce@exemplo.com"
              className="auth-input pl-10"
            />
          </div>

          {mutation.isError && (
            <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-3.5 py-2.5 text-sm text-danger-600">
              {getApiErrorMessage(mutation.error, 'Não foi possível enviar o link de recuperação.')}
            </p>
          )}

          <button
            type="submit"
            disabled={mutation.isPending || !email.trim()}
            className="primary-button mt-6 w-full py-3 disabled:cursor-not-allowed disabled:opacity-60 disabled:grayscale"
          >
            {mutation.isPending ? 'Enviando link...' : 'Enviar link de recuperação'}
          </button>

          <Link to="/login" className="mt-5 flex items-center justify-center gap-2 text-sm font-semibold text-text-600 hover:text-primary-700">
            <ArrowLeft size={15} /> Voltar para o login
          </Link>
        </form>
      )}
    </AuthShell>
  )
}
