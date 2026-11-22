/** Conduz a troca de senha de um usuário já autenticado. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { CheckCircle2, KeyRound, ShieldCheck } from 'lucide-react'
import { changePassword } from '@/features/auth/services/authApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { setStoredToken } from '@/lib/api'
import { CriterionItem, PasswordField } from '@/features/auth/components/PasswordFields'
import {
  PASSWORD_MAX_LENGTH as MAX_LENGTH,
  PASSWORD_MIN_LENGTH as MIN_LENGTH,
  recommendedCriteria,
} from '@/features/auth/passwordPolicy'

/**
 * US 2.19 — Alterar senha. Disponível para todos os perfis autenticados.
 * RNF.01.01: divergência entre nova senha e confirmação exibida em tempo real.
 * RNF.01.02: checklist dinâmico dos critérios de segurança.
 * RNF.03.01: botão desabilitado durante o envio para evitar envios duplicados.
 */
export function ChangePasswordPage() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: changePassword,
    onSuccess: (session) => {
      // A troca encerra todas as sessões; este dispositivo continua com o token novo.
      setStoredToken(session.token)
      setSuccessMessage('Senha alterada com sucesso. Por segurança, você foi desconectado dos outros dispositivos.')
      setCurrentPassword('')
      setNewPassword('')
      setConfirmation('')
    },
  })

  const criteria = [
    { label: `Pelo menos ${MIN_LENGTH} caracteres`, met: newPassword.length >= MIN_LENGTH && newPassword.length <= MAX_LENGTH },
    { label: 'Diferente da senha atual', met: newPassword.length > 0 && newPassword !== currentPassword },
    { label: 'Nova senha e confirmação iguais', met: confirmation.length > 0 && newPassword === confirmation },
  ]
  const recommendations = recommendedCriteria(newPassword)

  const confirmationMismatch = confirmation.length > 0 && newPassword !== confirmation
  const canSubmit = currentPassword.length > 0 && criteria.every((criterion) => criterion.met) && !mutation.isPending
  const errorMessage = mutation.isError
    ? getApiErrorMessage(mutation.error, 'Não foi possível alterar a senha.')
    : null

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!canSubmit) return
    setSuccessMessage(null)
    mutation.mutate({ currentPassword, newPassword, newPasswordConfirmation: confirmation })
  }

  function clearFeedback() {
    if (successMessage) setSuccessMessage(null)
    if (mutation.isError) mutation.reset()
  }

  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Minha conta</p>
        <h1 className="page-title mt-2">Alterar senha</h1>
        <p className="page-subtitle">Confirme sua senha atual e escolha uma nova senha para manter sua conta segura.</p>
      </header>

      <form onSubmit={submit} className="surface-card mt-7 max-w-xl overflow-hidden" noValidate>
        <div className="flex items-center gap-3 border-b border-background-300 bg-background-100/60 p-5 sm:p-6">
          <span className="grid size-10 place-items-center rounded-xl bg-primary-50 text-primary-700"><KeyRound size={19} /></span>
          <div>
            <h2 className="text-lg font-semibold text-text-900">Credenciais de acesso</h2>
          </div>
        </div>

        <fieldset disabled={mutation.isPending} className="space-y-5 p-5 sm:p-6">
          {successMessage && (
            <p role="status" className="flex items-center gap-2 rounded-xl bg-success-50 px-4 py-3 text-sm font-medium text-success-600">
              <CheckCircle2 size={17} /> {successMessage}
            </p>
          )}
          {errorMessage && (
            <p role="alert" className="rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>
          )}

          <PasswordField
            id="current-password"
            label="Senha atual"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(value) => { setCurrentPassword(value); clearFeedback() }}
          />
          <PasswordField
            id="new-password"
            label="Nova senha"
            autoComplete="new-password"
            value={newPassword}
            maxLength={MAX_LENGTH}
            onChange={(value) => { setNewPassword(value); clearFeedback() }}
          />
          <PasswordField
            id="new-password-confirmation"
            label="Confirmar nova senha"
            autoComplete="new-password"
            value={confirmation}
            maxLength={MAX_LENGTH}
            invalid={confirmationMismatch}
            onChange={(value) => { setConfirmation(value); clearFeedback() }}
          />
          {confirmationMismatch && (
            <p className="-mt-3 text-sm font-medium text-danger-600">A nova senha e a confirmação não correspondem</p>
          )}

          <div className="rounded-xl border border-background-300 bg-background-100/60 p-4">
            <p className="flex items-center gap-2 text-sm font-semibold text-text-800"><ShieldCheck size={16} /> Critérios da nova senha</p>
            <ul className="mt-3 space-y-1.5 text-sm">
              {criteria.map((criterion) => <CriterionItem key={criterion.label} {...criterion} />)}
              {recommendations.map((criterion) => <CriterionItem key={criterion.label} {...criterion} optional />)}
            </ul>
          </div>

          <button type="submit" disabled={!canSubmit} className="primary-button w-full py-3 disabled:cursor-not-allowed disabled:opacity-60">
            {mutation.isPending ? 'Salvando...' : 'Salvar nova senha'}
          </button>
        </fieldset>
      </form>
    </div>
  )
}
