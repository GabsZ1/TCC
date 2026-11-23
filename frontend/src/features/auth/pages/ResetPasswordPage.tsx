/** Redefine a senha usando o token recebido no fluxo de recuperação. */
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowRight, KeyRound, TriangleAlert } from 'lucide-react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { AuthShell } from '@/features/auth/components/AuthShell'
import { CriterionItem, PasswordField } from '@/features/auth/components/PasswordFields'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, recommendedCriteria } from '@/features/auth/domain/passwordPolicy'
import { resetPassword, validatePasswordResetToken } from '@/features/auth/api/authApi'
import { getApiErrorMessage } from '@/lib/apiError'

/**
 * US 2.20 — Redefinir a senha pelo link recebido por e-mail.
 * O link é validado assim que a página abre; depois de salvar, o usuário
 * volta para o login com a mensagem de sucesso (CT02).
 */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')

  const validation = useQuery({
    queryKey: ['password-reset-token', token],
    queryFn: () => validatePasswordResetToken(token),
    enabled: token.length > 0,
    retry: false,
    refetchOnWindowFocus: false,
  })

  const mutation = useMutation({
    mutationFn: resetPassword,
    onSuccess: () => navigate('/login', { replace: true, state: { message: 'Senha redefinida com sucesso' } }),
  })

  const criteria = [
    { label: `Pelo menos ${PASSWORD_MIN_LENGTH} caracteres`, met: password.length >= PASSWORD_MIN_LENGTH },
    { label: 'Nova senha e confirmação iguais', met: confirmation.length > 0 && password === confirmation },
  ]
  const confirmationMismatch = confirmation.length > 0 && password !== confirmation
  const canSubmit = criteria.every((criterion) => criterion.met) && !mutation.isPending

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!canSubmit) return
    mutation.mutate({ token, newPassword: password, newPasswordConfirmation: confirmation })
  }

  const linkError = !token
    ? 'Este link não é mais válido'
    : validation.isError
      ? getApiErrorMessage(validation.error, 'Este link não é mais válido')
      : null

  return (
    <AuthShell
      eyebrow="Recuperar acesso"
      title={linkError ? 'Link indisponível' : 'Crie uma nova senha'}
      subtitle={linkError ? 'Peça um novo link para continuar.' : 'Escolha uma senha nova para voltar a acessar sua conta.'}
      maxWidth="sm"
    >
      {linkError ? (
        <div className="text-center">
          <span className="mx-auto grid size-14 place-items-center rounded-2xl bg-danger-50 text-danger-600">
            <TriangleAlert size={26} />
          </span>
          <p role="alert" className="mt-5 text-sm font-medium leading-6 text-danger-600">{linkError}</p>
          <Link className="primary-button mt-6 w-full py-3" to="/recuperar-senha">Solicitar novo link <ArrowRight size={17} /></Link>
          <Link className="mt-4 block text-sm font-semibold text-text-600 hover:text-primary-700" to="/login">Voltar para o login</Link>
        </div>
      ) : validation.isPending ? (
        <p className="text-center text-sm text-text-600">Validando o link...</p>
      ) : (
        <form onSubmit={submit} className="space-y-5" noValidate>
          <div className="flex items-center gap-3 rounded-xl bg-primary-50 px-4 py-3 text-sm text-text-700">
            <KeyRound size={18} className="shrink-0 text-primary-700" />
            O link é de uso único. Depois de salvar, entre com a nova senha.
          </div>

          {mutation.isError && (
            <p role="alert" className="rounded-xl border border-danger-500/20 bg-danger-50 px-3.5 py-2.5 text-sm text-danger-600">
              {getApiErrorMessage(mutation.error, 'Não foi possível redefinir a senha.')}
            </p>
          )}

          <PasswordField
            id="reset-password"
            label="Nova senha"
            autoComplete="new-password"
            value={password}
            maxLength={PASSWORD_MAX_LENGTH}
            onChange={(value) => { setPassword(value); if (mutation.isError) mutation.reset() }}
          />
          <PasswordField
            id="reset-password-confirmation"
            label="Confirmar nova senha"
            autoComplete="new-password"
            value={confirmation}
            maxLength={PASSWORD_MAX_LENGTH}
            invalid={confirmationMismatch}
            onChange={(value) => { setConfirmation(value); if (mutation.isError) mutation.reset() }}
          />
          {confirmationMismatch && (
            <p className="-mt-3 text-sm font-medium text-danger-600">A nova senha e a confirmação não correspondem</p>
          )}

          <ul className="space-y-1.5 rounded-xl border border-background-300 bg-background-100/60 p-4 text-sm">
            {criteria.map((criterion) => <CriterionItem key={criterion.label} {...criterion} />)}
            {recommendedCriteria(password).map((criterion) => <CriterionItem key={criterion.label} {...criterion} optional />)}
          </ul>

          <button type="submit" disabled={!canSubmit} className="primary-button w-full py-3 disabled:cursor-not-allowed disabled:opacity-60">
            {mutation.isPending ? 'Salvando...' : 'Redefinir senha'}
          </button>
        </form>
      )}
    </AuthShell>
  )
}
