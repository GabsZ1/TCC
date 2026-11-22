/** Permite ao profissional consultar e atualizar o próprio perfil. */
import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { IMaskInput } from 'react-imask'
import { CheckCircle2, ImagePlus, KeyRound, Lock, Pencil, Trash2 } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { PasswordField } from '@/features/auth/components/PasswordFields'
import { EmployeeAvatar } from '@/features/employees/components/EmployeeAvatar'
import {
  deleteMyPhoto,
  fetchMyEmployeeProfile,
  updateMyEmployeeProfile,
  uploadMyPhoto,
  type Employee,
  type EmployeeSelfProfileInput,
} from '@/features/employees/services/employeesApi'
import { useAuth } from '@/hooks/useAuth'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatCpf } from '@/lib/cpf'
import { formatDate } from '@/lib/format'
import { formatPhone, isValidPhone, PHONE_MASK } from '@/lib/phone'

const PROFILE_KEY = ['my-employee-profile']
const MAX_PHOTO_BYTES = 2 * 1024 * 1024
const ACCEPTED_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp']

type FormState = { email: string; phone: string; instagram: string; address: string }

/**
 * Perfil do funcionário: ele mesmo atualiza e-mail de acesso (com a senha atual),
 * telefone, Instagram, endereço e foto. Nome e ocupação são alterados pelo
 * administrador; CPF e data de admissão são fixos.
 */
export function ProfessionalProfilePage() {
  const queryClient = useQueryClient()
  const { refreshUser } = useAuth()
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchMyEmployeeProfile })
  const [form, setForm] = useState<FormState | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: updateMyEmployeeProfile,
    onSuccess: async (updated) => {
      queryClient.setQueryData(PROFILE_KEY, updated)
      setConfirming(false)
      setForm(null)
      setNotice('Dados atualizados com sucesso!')
      await refreshUser().catch(() => undefined)
    },
  })

  function startEditing(current: Employee) {
    setNotice(null)
    setFormError(null)
    save.reset()
    setForm({
      email: current.email,
      phone: current.phone ? formatPhone(current.phone) : '',
      instagram: current.instagram ? `@${current.instagram}` : '',
      address: current.address ?? '',
    })
  }

  function requestSave(event: FormEvent) {
    event.preventDefault()
    if (!form) return
    if (!form.email.trim()) return setFormError('Preencha todos os campos obrigatórios!')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return setFormError('Email inválido')
    if (form.phone.trim() && !isValidPhone(form.phone)) return setFormError('Informe um telefone válido!')
    if (form.instagram.trim() && !/^@?[A-Za-z0-9._]{1,30}$/.test(form.instagram.trim())) return setFormError('Instagram inválido. Use até 30 letras, números, ponto ou sublinhado.')
    setFormError(null)
    save.reset()
    setConfirming(true)
  }

  const current = profile.data
  const emailChanged = !!form && !!current && form.email.trim().toLowerCase() !== current.email

  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Minha conta</p>
        <h1 className="page-title mt-2">Meu perfil</h1>
        <p className="page-subtitle">Mantenha seus dados de contato e sua foto atualizados.</p>
      </header>

      {notice && <p role="status" className="mt-6 flex max-w-2xl items-center gap-2 rounded-xl bg-success-50 px-4 py-3 text-sm font-medium text-success-600"><CheckCircle2 size={17} /> {notice}</p>}
      {profile.isLoading && <StateCard>Carregando seus dados...</StateCard>}
      {profile.isError && <StateCard error>{getApiErrorMessage(profile.error, 'Não foi possível carregar seu perfil.')}</StateCard>}

      {current && (
        <div className="mt-6 max-w-2xl space-y-6">
          <PhotoCard employee={current} onChanged={(message) => { setNotice(message); queryClient.invalidateQueries({ queryKey: PROFILE_KEY }) }} />

          <section className="surface-card overflow-hidden">
            <div className="border-b border-background-300 bg-background-100/60 p-5">
              <h2 className="text-lg font-semibold text-text-900">Dados cadastrais</h2>
              <p className="text-sm text-text-600">Nome e ocupação são definidos pelo estabelecimento. Para corrigi-los, fale com o administrador.</p>
            </div>
            <dl className="divide-y divide-background-300">
              <ReadOnlyRow label="Nome" value={current.name} />
              <ReadOnlyRow label="Ocupação" value={current.occupation ?? 'Não informada'} />
              <ReadOnlyRow label="CPF" value={current.cpf ? formatCpf(current.cpf) : 'Não informado'} />
              <ReadOnlyRow label="Data de admissão" value={current.hireDate ? formatDate(current.hireDate) : 'Não informada'} />
              <ReadOnlyRow label="Estabelecimento" value={current.establishmentName ?? '—'} />
            </dl>
          </section>

          <section className="surface-card overflow-hidden">
            <div className="flex flex-col gap-3 border-b border-background-300 bg-background-100/60 p-5 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-lg font-semibold text-text-900">Contato</h2>
              </div>
              {!form && <button type="button" className="secondary-button w-full sm:w-auto" onClick={() => startEditing(current)}><Pencil size={15} /> Editar</button>}
            </div>

            {!form ? (
              <dl className="divide-y divide-background-300">
                <InfoRow label="E-mail de acesso" value={current.email} />
                <InfoRow label="Telefone" value={current.phone ? formatPhone(current.phone) : 'Não informado'} />
                <InfoRow label="Instagram" value={current.instagram ? `@${current.instagram}` : 'Não informado'} />
                <InfoRow label="Endereço" value={current.address ?? 'Não informado'} />
              </dl>
            ) : (
              <form onSubmit={requestSave} noValidate className="space-y-4 p-5">
                <p className="text-xs text-text-500">Campos com <span className="font-bold text-danger-600">*</span> são obrigatórios.</p>
                <Field label="E-mail de acesso" required hint="É o e-mail usado para entrar no sistema. Alterá-lo exige sua senha atual.">
                  <input type="email" value={form.email} maxLength={150} onChange={(event) => setForm({ ...form, email: event.target.value })} className="field-control" />
                </Field>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Telefone">
                    <IMaskInput mask={PHONE_MASK} value={form.phone} onAccept={(value: string) => setForm({ ...form, phone: value })} inputMode="tel" placeholder="(00) 00000-0000" className="field-control" />
                  </Field>
                  <Field label="Instagram">
                    <input value={form.instagram} maxLength={31} onChange={(event) => setForm({ ...form, instagram: event.target.value })} className="field-control" placeholder="@perfil" />
                  </Field>
                </div>
                <Field label="Endereço">
                  <input value={form.address} maxLength={255} onChange={(event) => setForm({ ...form, address: event.target.value })} className="field-control" placeholder="Rua, número, bairro e cidade" />
                </Field>

                {formError && <p role="alert" className="rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{formError}</p>}

                <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                  <button type="button" className="secondary-button w-full sm:w-auto" onClick={() => { setForm(null); setFormError(null) }}>Cancelar</button>
                  <button type="submit" className="primary-button w-full sm:w-auto">Revisar alterações</button>
                </div>
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
        </div>
      )}

      {confirming && form && (
        <ConfirmSaveDialog
          form={form}
          emailChanged={emailChanged}
          isSaving={save.isPending}
          errorMessage={save.isError ? getApiErrorMessage(save.error, 'Não foi possível salvar as alterações.') : null}
          onCancel={() => setConfirming(false)}
          onConfirm={(currentPassword) => {
            const input: EmployeeSelfProfileInput = {
              email: form.email.trim(),
              phone: form.phone.trim() || undefined,
              instagram: form.instagram.trim() || undefined,
              address: form.address.trim() || undefined,
              currentPassword: emailChanged ? currentPassword : undefined,
            }
            save.mutate(input)
          }}
        />
      )}
    </div>
  )
}

/** Mostra as alterações propostas antes de salvar o perfil profissional. */
function ConfirmSaveDialog({ form, emailChanged, isSaving, errorMessage, onCancel, onConfirm }: {
  form: FormState
  emailChanged: boolean
  isSaving: boolean
  errorMessage: string | null
  onCancel: () => void
  onConfirm: (currentPassword: string) => void
}) {
  const [password, setPassword] = useState('')
  return (
    <Modal title="Confirmar alterações" onClose={onCancel}>
      <p className="text-sm text-text-700">Deseja salvar estas informações no seu perfil?</p>
      <ul className="mt-3 space-y-1 rounded-xl bg-background-100 p-4 text-sm text-text-700">
        <li><strong>E-mail:</strong> {form.email.trim()}</li>
        <li><strong>Telefone:</strong> {form.phone.trim() || 'Não informado'}</li>
        <li><strong>Instagram:</strong> {form.instagram.trim() || 'Não informado'}</li>
        <li><strong>Endereço:</strong> {form.address.trim() || 'Não informado'}</li>
      </ul>
      {emailChanged && (
        <div className="mt-4">
          <p className="mb-2 text-sm text-text-700">Você está trocando o e-mail de acesso. A partir de agora, o login será com <strong>{form.email.trim()}</strong>.</p>
          <PasswordField id="profile-current-password" label="Senha atual" autoComplete="current-password" value={password} onChange={setPassword} />
        </div>
      )}
      {errorMessage && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}
      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" className="secondary-button" onClick={onCancel}>Voltar</button>
        <button type="button" className="primary-button" disabled={isSaving || (emailChanged && !password)} onClick={() => onConfirm(password)}>
          {isSaving ? 'Salvando...' : 'Confirmar'}
        </button>
      </div>
    </Modal>
  )
}

/** Coordena envio e remoção da foto exibida no perfil e no catálogo. */
function PhotoCard({ employee, onChanged }: { employee: Employee; onChanged: (message: string) => void }) {
  const queryClient = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [selected, setSelected] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const previewUrl = useMemo(() => (selected ? URL.createObjectURL(selected) : null), [selected])
  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl) }, [previewUrl])

  const refreshPhoto = () => queryClient.invalidateQueries({ queryKey: ['employee-photo', employee.id] })
  const upload = useMutation({
    mutationFn: uploadMyPhoto,
    onSuccess: () => { setSelected(null); refreshPhoto(); onChanged('Foto atualizada com sucesso!') },
    onError: (requestError) => setError(getApiErrorMessage(requestError, 'Não foi possível enviar a foto.')),
  })
  const remove = useMutation({
    mutationFn: deleteMyPhoto,
    onSuccess: () => { refreshPhoto(); onChanged('Foto removida.') },
    onError: (requestError) => setError(getApiErrorMessage(requestError, 'Não foi possível remover a foto.')),
  })

  function choose(file: File | undefined) {
    setError(null)
    if (!file) return
    if (!ACCEPTED_PHOTO_TYPES.includes(file.type)) return setError('Use uma imagem JPG, PNG ou WEBP.')
    if (file.size > MAX_PHOTO_BYTES) return setError('A foto deve ter no máximo 2 MB.')
    setSelected(file)
  }

  const busy = upload.isPending || remove.isPending
  return (
    <section className="surface-card flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
      {previewUrl
        ? <img src={previewUrl} alt="Pré-visualização da nova foto" className="size-20 shrink-0 rounded-2xl object-cover" />
        : employee.hasPhoto
          ? <EmployeeAvatar id={employee.id} name={employee.name} hasPhoto size="lg" />
          : <span className="grid size-20 shrink-0 place-items-center rounded-2xl border border-dashed border-background-400 text-text-400"><ImagePlus size={24} /></span>}
      <div className="flex-1">
        <h2 className="font-semibold text-text-900">Foto</h2>
        <p className="text-sm text-text-600">JPG, PNG ou WEBP, até 2 MB. Aparece para o administrador na lista da equipe.</p>
        <div className="mt-3 flex flex-wrap gap-2">
          {selected ? (
            <>
              <button type="button" className="primary-button px-3 py-1.5 text-sm" disabled={busy} onClick={() => upload.mutate(selected)}>{upload.isPending ? 'Enviando...' : 'Salvar foto'}</button>
              <button type="button" className="secondary-button px-3 py-1.5 text-sm" disabled={busy} onClick={() => setSelected(null)}>Cancelar</button>
            </>
          ) : (
            <>
              <button type="button" className="secondary-button px-3 py-1.5 text-sm" disabled={busy} onClick={() => input.current?.click()}>{employee.hasPhoto ? 'Trocar foto' : 'Escolher foto'}</button>
              {employee.hasPhoto && (
                <button type="button" className="inline-flex items-center gap-1 rounded-lg px-2 py-1.5 text-sm font-semibold text-danger-600 hover:bg-danger-50" disabled={busy} onClick={() => remove.mutate()}>
                  <Trash2 size={14} /> Remover
                </button>
              )}
            </>
          )}
        </div>
        <input ref={input} type="file" accept={ACCEPTED_PHOTO_TYPES.join(',')} className="hidden" onChange={(event) => { choose(event.target.files?.[0]); event.target.value = '' }} />
        {error && <p className="mt-2 text-sm text-danger-600">{error}</p>}
      </div>
    </section>
  )
}

function ReadOnlyRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 px-5 py-3.5 text-sm">
      <Lock size={14} className="shrink-0 text-text-400" />
      <dt className="w-36 shrink-0 text-text-600">{label}</dt>
      <dd className="min-w-0 break-words font-semibold text-text-900">{value}</dd>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-3 px-5 py-3.5 text-sm">
      <dt className="w-40 shrink-0 text-text-600">{label}</dt>
      <dd className="min-w-0 break-words font-semibold text-text-900">{value}</dd>
    </div>
  )
}

function Field({ label, required = false, hint, children }: { label: string; required?: boolean; hint?: string; children: ReactNode }) {
  return (
    <label className="block text-sm font-semibold text-text-800">
      {label} {required && <span className="font-bold text-danger-600" aria-hidden="true">*</span>}
      {children}
      {hint && <span className="mt-1 block text-xs font-normal text-text-500">{hint}</span>}
    </label>
  )
}

function StateCard({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return <div className={`mt-6 max-w-2xl rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div>
}
