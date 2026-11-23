/** Apresenta o perfil de um cliente no contexto do estabelecimento. */
import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { IMaskInput } from 'react-imask'
import { CheckCircle2, Info, KeyRound, Mail, Pencil, Phone, Trash2, TriangleAlert, UserRound } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { PasswordField } from '@/features/auth/components/PasswordFields'
import {
  closeClientAccount,
  fetchClientProfile,
  updateClientProfile,
  type ClientProfile,
  type ClientProfileInput,
} from '@/features/client/api/clientAccountApi'
import { useAuth } from '@/features/auth/session/useAuth'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatPhone, isValidPhone, PHONE_MASK } from '@/lib/phone'

const PROFILE_KEY = ['client-profile']

/**
 * Conta do cliente:
 * - US 2.25 Visualizar perfil (somente leitura, com acesso à edição);
 * - US 2.24 Editar perfil (carrega os dados atuais, pede confirmação antes de salvar, permite cancelar);
 * - US 2.26 Excluir conta (confirmação explícita com a senha, pode ser cancelada).
 */
export function ClientProfilePage() {
  const queryClient = useQueryClient()
  const { refreshUser, logout } = useAuth()
  const navigate = useNavigate()
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchClientProfile })

  const [form, setForm] = useState<ClientProfileInput | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [confirmingSave, setConfirmingSave] = useState(false)
  const [notice, setNotice] = useState<{ kind: 'success' | 'info'; text: string } | null>(null)
  const [closingAccount, setClosingAccount] = useState(false)

  const saveMutation = useMutation({
    mutationFn: updateClientProfile,
    onSuccess: async (updated, input) => {
      const emailChanged = updated.email !== profile.data?.email
      queryClient.setQueryData(PROFILE_KEY, updated)
      setConfirmingSave(false)
      setForm(null)
      setNotice({ kind: 'success', text: emailChanged && input.email ? 'Dados atualizados com sucesso! Use o novo e-mail no próximo login.' : 'Dados atualizados com sucesso!' })
      await refreshUser().catch(() => undefined)
    },
    onError: () => setConfirmingSave(false),
  })

  function startEditing(current: ClientProfile) {
    setNotice(null)
    setFormError(null)
    saveMutation.reset()
    setForm({ name: current.name, email: current.email, phone: current.phone ?? '' })
  }

  function cancelEditing() {
    setForm(null)
    setFormError(null)
    saveMutation.reset()
  }

  function requestSave(event: FormEvent) {
    event.preventDefault()
    if (!form) return
    if (!form.name.trim() || !form.email.trim() || !form.phone.trim()) return setFormError('Preencha todos os campos obrigatórios!')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return setFormError('Informe um e-mail válido!')
    if (!isValidPhone(form.phone)) return setFormError('Informe um telefone válido!')
    setFormError(null)
    setConfirmingSave(true)
  }

  function handleAccountClosed() {
    navigate('/login', { replace: true, state: { message: 'Sua conta foi excluída com sucesso!' } })
    logout()
  }

  const saveError = saveMutation.isError ? getApiErrorMessage(saveMutation.error, 'Não foi possível salvar as alterações.') : null

  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Minha conta</p>
        <h1 className="page-title mt-2">Meu perfil</h1>
        <p className="page-subtitle">Consulte e mantenha atualizados os seus dados de contato.</p>
      </header>

      {notice && (
        <p role="status" className={`mt-6 flex max-w-2xl items-center gap-2 rounded-xl px-4 py-3 text-sm font-medium ${notice.kind === 'success' ? 'bg-success-50 text-success-600' : 'bg-background-200 text-text-700'}`}>
          {notice.kind === 'success' ? <CheckCircle2 size={17} /> : <Info size={17} />} {notice.text}
        </p>
      )}

      {profile.isLoading && <StateCard>Carregando seus dados...</StateCard>}
      {profile.isError && <StateCard error>{getApiErrorMessage(profile.error, 'Não foi possível carregar seu perfil.')}</StateCard>}

      {profile.data && (
        <div className="mt-6 max-w-2xl space-y-6">
          <section className="surface-card overflow-hidden">
            <div className="flex flex-col gap-3 border-b border-background-300 bg-background-100/60 p-5 sm:flex-row sm:items-center sm:justify-between">
              <h2 className="text-lg font-semibold text-text-900">Dados pessoais</h2>
              {!form && (
                <button type="button" className="secondary-button w-full sm:w-auto" onClick={() => startEditing(profile.data)}>
                  <Pencil size={15} /> Editar perfil
                </button>
              )}
            </div>

            {!form ? (
              <dl className="divide-y divide-background-300">
                <ProfileRow icon={<UserRound size={17} />} label="Nome" value={profile.data.name} />
                <ProfileRow icon={<Mail size={17} />} label="E-mail" value={profile.data.email} />
                <ProfileRow icon={<Phone size={17} />} label="Telefone" value={profile.data.phone ? formatPhone(profile.data.phone) : 'Não informado'} />
              </dl>
            ) : (
              <form onSubmit={requestSave} noValidate>
                <fieldset disabled={saveMutation.isPending} className="space-y-5 p-5">
                  <p className="text-xs text-text-500">Campos marcados com <span className="font-bold text-danger-600">*</span> são obrigatórios.</p>
                  <Field label="Nome">
                    <input value={form.name} maxLength={150} autoComplete="name" onChange={(event) => setForm({ ...form, name: event.target.value })} className="field-control" />
                  </Field>
                  <Field label="E-mail">
                    <input type="email" value={form.email} maxLength={150} autoComplete="email" onChange={(event) => setForm({ ...form, email: event.target.value })} className="field-control" />
                  </Field>
                  <Field label="Telefone">
                    <IMaskInput mask={PHONE_MASK} value={formatPhone(form.phone)} onAccept={(value: string) => setForm({ ...form, phone: value })} inputMode="tel" autoComplete="tel" placeholder="(00) 00000-0000" className="field-control" />
                  </Field>

                  {(formError || saveError) && (
                    <p role="alert" className="rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{formError ?? saveError}</p>
                  )}

                  <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                    <button type="button" className="secondary-button w-full sm:w-auto" onClick={cancelEditing}>Cancelar</button>
                    <button type="submit" className="primary-button w-full sm:w-auto">Revisar alterações</button>
                  </div>
                </fieldset>
              </form>
            )}
          </section>

          <section className="surface-card flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-3">
              <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary-50 text-primary-700"><KeyRound size={18} /></span>
              <div>
                <h2 className="font-semibold text-text-900">Senha</h2>
              </div>
            </div>
            <Link to="/alterar-senha" className="secondary-button shrink-0">Alterar senha</Link>
          </section>

          <section className="rounded-2xl border border-danger-500/30 bg-danger-50/40 p-5">
            <div className="flex items-start gap-3">
              <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-danger-50 text-danger-600"><TriangleAlert size={18} /></span>
              <div className="flex-1">
                <h2 className="font-semibold text-text-900">Excluir conta</h2>
                <p className="mt-1 text-sm leading-6 text-text-600">
                  Seu acesso será encerrado e seus dados pessoais removidos. Agendamentos futuros serão cancelados; o histórico de atendimentos já realizados continua com os estabelecimentos.
                </p>
                <button type="button" onClick={() => { setNotice(null); setClosingAccount(true) }} className="mt-4 inline-flex items-center gap-2 rounded-[0.72rem] border border-danger-500/40 bg-white px-4 py-2.5 text-sm font-bold text-danger-600 hover:bg-danger-50">
                  <Trash2 size={15} /> Excluir conta
                </button>
              </div>
            </div>
          </section>
        </div>
      )}

      {confirmingSave && form && (
        <Modal title="Confirmar alterações" onClose={() => setConfirmingSave(false)}>
          <p className="text-sm leading-6 text-text-700">Deseja salvar as alterações no seu perfil?</p>
          <ul className="mt-3 space-y-1 rounded-xl bg-background-100 p-4 text-sm text-text-700">
            <li><strong>Nome:</strong> {form.name.trim()}</li>
            <li><strong>E-mail:</strong> {form.email.trim()}</li>
            <li><strong>Telefone:</strong> {formatPhone(form.phone)}</li>
          </ul>
          <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <button type="button" className="secondary-button" onClick={() => setConfirmingSave(false)}>Voltar</button>
            <button type="button" className="primary-button" disabled={saveMutation.isPending} onClick={() => saveMutation.mutate({ ...form, name: form.name.trim(), email: form.email.trim() })}>
              {saveMutation.isPending ? 'Salvando...' : 'Confirmar'}
            </button>
          </div>
        </Modal>
      )}

      {closingAccount && (
        <CloseAccountDialog
          onCancel={() => { setClosingAccount(false); setNotice({ kind: 'info', text: 'A exclusão da conta foi cancelada!' }) }}
          onClosed={handleAccountClosed}
        />
      )}
    </div>
  )
}

/** Confirma a senha antes de encerrar a conta e cancelar reservas futuras. */
function CloseAccountDialog({ onCancel, onClosed }: { onCancel: () => void; onClosed: () => void }) {
  const [password, setPassword] = useState('')
  const mutation = useMutation({ mutationFn: closeClientAccount, onSuccess: onClosed })

  return (
    <Modal title="Excluir minha conta" onClose={onCancel}>
      <div className="flex items-start gap-3 rounded-[0.8rem] border border-danger-500/20 bg-danger-50 p-4 text-sm leading-6 text-text-700">
        <TriangleAlert size={18} className="mt-0.5 shrink-0 text-danger-600" />
        <p>Esta ação afeta sua conta e não pode ser desfeita: você não conseguirá mais entrar com ela e seus agendamentos futuros serão cancelados.</p>
      </div>
      <form className="mt-5" onSubmit={(event) => { event.preventDefault(); if (password) mutation.mutate(password) }}>
        <PasswordField id="close-account-password" label="Digite sua senha para confirmar" autoComplete="current-password" value={password} onChange={(value) => { setPassword(value); if (mutation.isError) mutation.reset() }} />
        {mutation.isError && (
          <p role="alert" className="mt-4 rounded-[0.8rem] border border-danger-500/20 bg-danger-50 px-3.5 py-3 text-sm text-danger-600">
            {getApiErrorMessage(mutation.error, 'Não foi possível excluir a conta.')}
          </p>
        )}
        <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button type="button" onClick={onCancel} className="secondary-button">Cancelar</button>
          <button type="submit" disabled={!password || mutation.isPending} className="inline-flex items-center justify-center rounded-[0.72rem] bg-danger-600 px-4 py-2.5 text-sm font-bold text-on-danger shadow-sm hover:bg-danger-500 disabled:cursor-not-allowed disabled:opacity-55">
            {mutation.isPending ? 'Excluindo...' : 'Confirmar exclusão'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ProfileRow({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 px-5 py-4">
      <span className="text-text-500">{icon}</span>
      <dt className="w-24 shrink-0 text-sm font-medium text-text-600">{label}</dt>
      <dd className="min-w-0 truncate text-sm font-semibold text-text-900">{value}</dd>
    </div>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="block text-sm font-semibold text-text-800">{label} <span className="font-bold text-danger-600" aria-hidden="true">*</span>{children}</label>
}

function StateCard({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return <div className={`mt-6 max-w-2xl rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div>
}
