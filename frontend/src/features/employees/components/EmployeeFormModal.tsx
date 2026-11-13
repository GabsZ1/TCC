/** Coleta e valida os dados usados para criar ou editar um funcionário. */
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { IMaskInput } from 'react-imask'
import { z } from 'zod'
import { Lock, TriangleAlert } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import type { Employee, EmployeeCreateInput, EmployeeUpdateInput } from '@/features/employees/services/employeesApi'
import { cpfDigits, formatCpf, isValidCpf } from '@/lib/cpf'
import { formatDate, todayIso } from '@/lib/format'
import { OCCUPATION_SUGGESTIONS } from '@/features/employees/occupations'

const cpfField = z.string().trim().refine((value) => value === '' || isValidCpf(value), 'CPF inválido')
const hireDateField = z.string().refine((value) => value === '' || value <= todayIso(), 'A data de admissão não pode ser futura')

const createSchema = z.object({
  name: z.string().trim().min(1, 'Nome é um campo obrigatório!').max(150),
  email: z.string().trim().min(1, 'Email é um campo obrigatório!').email('Email inválido').max(150),
  occupation: z.string().trim().min(1, 'Ocupação é um campo obrigatório!').max(80),
  cpf: cpfField.refine((value) => value !== '', 'CPF é um campo obrigatório!'),
  hireDate: hireDateField.refine((value) => value !== '', 'Data de admissão é um campo obrigatório!'),
})
const editSchema = z.object({
  name: z.string().trim().min(1, 'Nome é um campo obrigatório!').max(150),
  email: z.string(),
  occupation: z.string().trim().min(1, 'Ocupação é um campo obrigatório!').max(80),
  cpf: cpfField,
  hireDate: hireDateField,
})
type FormValues = z.infer<typeof createSchema>

interface EmployeeFormModalProps {
  employee?: Employee | null
  isSubmitting: boolean
  errorMessage?: string | null
  onCreate: (values: EmployeeCreateInput) => void
  onUpdate: (values: EmployeeUpdateInput) => void
  onClose: () => void
}

/**
 * Cadastro/edição pelo administrador.
 * - Cadastro: nome, e-mail, ocupação, CPF e data de admissão (todos obrigatórios).
 * - Edição: nome e ocupação; CPF e data de admissão ficam travados depois de gravados e só
 *   podem ser preenchidos se ainda estiverem vazios (funcionários antigos).
 * - Contatos, foto e e-mail são do próprio funcionário ("Meu perfil").
 * Sempre que CPF/data de admissão forem gravados há dupla confirmação: revisão + redigitar o CPF.
 */
export function EmployeeFormModal({ employee, isSubmitting, errorMessage, onCreate, onUpdate, onClose }: EmployeeFormModalProps) {
  const isEditing = !!employee
  const cpfLocked = isEditing && !!employee?.cpf
  const hireDateLocked = isEditing && !!employee?.hireDate
  const [review, setReview] = useState<FormValues | null>(null)

  const { register, control, handleSubmit, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(isEditing ? editSchema : createSchema) as never,
    defaultValues: {
      name: employee?.name ?? '',
      email: employee?.email ?? '',
      occupation: employee?.occupation ?? '',
      cpf: '',
      hireDate: '',
    },
  })

  function submit(values: FormValues) {
    setReview(values)
  }

  function confirm(values: FormValues) {
    if (!isEditing) {
      onCreate({ name: values.name.trim(), email: values.email.trim(), occupation: values.occupation.trim(), cpf: cpfDigits(values.cpf), hireDate: values.hireDate })
      return
    }
    onUpdate({
      name: values.name.trim(),
      occupation: values.occupation.trim(),
      cpf: !cpfLocked && values.cpf ? cpfDigits(values.cpf) : undefined,
      hireDate: !hireDateLocked && values.hireDate ? values.hireDate : undefined,
    })
  }

  if (review) {
    const recordsDocuments = (!cpfLocked && review.cpf !== '') || (!hireDateLocked && review.hireDate !== '')
    return (
      <ReviewDialog
        values={review}
        isEditing={isEditing}
        recordsDocuments={recordsDocuments}
        isSubmitting={isSubmitting}
        errorMessage={errorMessage}
        onBack={() => setReview(null)}
        onConfirm={() => confirm(review)}
      />
    )
  }

  return (
    <Modal title={isEditing ? 'Editar funcionário' : 'Novo funcionário'} onClose={onClose}>
      <form onSubmit={handleSubmit(submit)} noValidate>
        <p className="mb-5 text-sm leading-6 text-text-600">
          {isEditing
            ? 'O administrador altera nome e ocupação. E-mail, contatos e foto são atualizados pelo próprio funcionário em "Meu perfil".'
            : 'O funcionário receberá por e-mail o link para definir a senha. Depois, ele mesmo completa contatos e foto no perfil.'}
          {' '}Campos com <span className="font-bold text-danger-600">*</span> são obrigatórios.
        </p>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Nome" required error={errors.name?.message} className="sm:col-span-2">
            <input type="text" autoFocus maxLength={150} {...register('name')} className={inputClass(errors.name)} placeholder="Nome completo" />
          </Field>

          {!isEditing && (
            <>
              <Field label="E-mail" required error={errors.email?.message}>
                <input type="email" maxLength={150} {...register('email')} className={inputClass(errors.email)} placeholder="profissional@exemplo.com" />
              </Field>
            </>
          )}

          <Field label="Ocupação" required error={errors.occupation?.message}>
            <input list="employee-occupations" maxLength={80} {...register('occupation')} className={inputClass(errors.occupation)} placeholder="Ex.: Cabeleireiro(a)" />
            <datalist id="employee-occupations">{OCCUPATION_SUGGESTIONS.map((occupation) => <option key={occupation} value={occupation} />)}</datalist>
          </Field>

          {cpfLocked ? (
            <LockedField label="CPF" value={formatCpf(employee?.cpf)} />
          ) : (
            <Field label="CPF" required={!isEditing} error={errors.cpf?.message}>
              <Controller name="cpf" control={control} render={({ field }) => (
                <IMaskInput mask="000.000.000-00" value={field.value} onAccept={(value: string) => field.onChange(value)} onBlur={field.onBlur} inputMode="numeric" placeholder="000.000.000-00" className={inputClass(errors.cpf)} />
              )} />
            </Field>
          )}

          {hireDateLocked ? (
            <LockedField label="Data de admissão" value={formatDate(employee!.hireDate!)} />
          ) : (
            <Field label="Data de admissão" required={!isEditing} error={errors.hireDate?.message}>
              <input type="date" max={todayIso()} {...register('hireDate')} className={inputClass(errors.hireDate)} />
            </Field>
          )}
        </div>

        {(!cpfLocked || !hireDateLocked) && (
          <p className="mt-4 flex items-start gap-2 rounded-xl bg-warning-50 px-3.5 py-3 text-xs leading-5 text-text-700">
            <TriangleAlert size={15} className="mt-0.5 shrink-0 text-warning-700" />
            CPF e data de admissão não poderão ser alterados depois de gravados — nem pelo administrador, nem pelo funcionário.
          </p>
        )}

        {errorMessage && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}

        <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button type="button" onClick={onClose} className="secondary-button">Cancelar</button>
          <button type="submit" className="primary-button">Continuar</button>
        </div>
      </form>
    </Modal>
  )
}

/** Confirmação antes de salvar; quando grava CPF/data de admissão, exige redigitar o CPF. */
function ReviewDialog({ values, isEditing, recordsDocuments, isSubmitting, errorMessage, onBack, onConfirm }: {
  values: FormValues
  isEditing: boolean
  recordsDocuments: boolean
  isSubmitting: boolean
  errorMessage?: string | null
  onBack: () => void
  onConfirm: () => void
}) {
  const [typedCpf, setTypedCpf] = useState('')
  const cpfToConfirm = cpfDigits(values.cpf)
  const needsTypedCpf = recordsDocuments && cpfToConfirm !== ''
  const canConfirm = !isSubmitting && (!needsTypedCpf || cpfDigits(typedCpf) === cpfToConfirm)

  return (
    <Modal title={isEditing ? 'Confirmar alterações' : 'Confirmar cadastro'} onClose={onBack}>
      <dl className="divide-y divide-background-300 rounded-xl border border-background-300 text-sm">
        <ReviewRow label="Nome" value={values.name.trim()} />
        {!isEditing && <ReviewRow label="E-mail" value={values.email.trim()} />}
        <ReviewRow label="Ocupação" value={values.occupation.trim()} />
        {values.cpf && <ReviewRow label="CPF" value={formatCpf(cpfToConfirm)} highlight={recordsDocuments} />}
        {values.hireDate && <ReviewRow label="Data de admissão" value={formatDate(values.hireDate)} highlight={recordsDocuments} />}
      </dl>

      {recordsDocuments && (
        <div className="mt-4 rounded-xl border border-warning-500/30 bg-warning-50 p-4 text-sm text-text-700">
          <p className="flex items-center gap-2 font-semibold text-text-900"><Lock size={15} /> Confira com atenção</p>
          <p className="mt-1 leading-6">Depois de gravados, <strong>CPF e data de admissão não poderão ser alterados</strong> por ninguém.</p>
          {needsTypedCpf && (
            <label className="mt-3 block text-sm font-semibold text-text-800">
              Digite o CPF novamente para confirmar
              <IMaskInput mask="000.000.000-00" value={typedCpf} onAccept={(value: string) => setTypedCpf(value)} inputMode="numeric" placeholder="000.000.000-00" className="field-control" autoFocus />
              {typedCpf.length === 14 && cpfDigits(typedCpf) !== cpfToConfirm && (
                <span className="mt-1.5 block text-sm font-normal text-danger-600">O CPF digitado não confere. Volte e corrija se necessário.</span>
              )}
            </label>
          )}
        </div>
      )}

      {errorMessage && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}

      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" className="secondary-button" onClick={onBack}>Voltar e corrigir</button>
        <button type="button" className="primary-button" disabled={!canConfirm} onClick={onConfirm}>
          {isSubmitting ? 'Salvando...' : 'Confirmar'}
        </button>
      </div>
    </Modal>
  )
}

function ReviewRow({ label, value, highlight = false }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div className={`flex gap-3 px-4 py-2.5 ${highlight ? 'bg-warning-50/60' : ''}`}>
      <dt className="w-32 shrink-0 text-text-500">{label}</dt>
      <dd className="min-w-0 break-words font-semibold text-text-900">{value}</dd>
    </div>
  )
}

function LockedField({ label, value }: { label: string; value: string }) {
  return (
    <div className="text-sm font-semibold text-text-800">
      {label}
      <p className="mt-2 flex items-center gap-2 rounded-[0.72rem] border border-background-300 bg-background-100 px-3.5 py-2.5 font-medium text-text-700">
        <Lock size={14} className="text-text-400" /> {value}
      </p>
    </div>
  )
}

function inputClass(error?: { message?: string }) {
  return `field-control ${error ? 'border-danger-500 focus:border-danger-500 focus:ring-danger-500/10' : ''}`
}

function Field({ label, required = false, error, className = '', children }: { label: string; required?: boolean; error?: string; className?: string; children: React.ReactNode }) {
  return (
    <label className={`block text-sm font-semibold text-text-800 ${className}`}>
      {label} {required && <span className="font-bold text-danger-600" aria-hidden="true">*</span>}
      {children}
      {error && <span className="mt-1.5 block text-sm font-normal text-danger-600">{error}</span>}
    </label>
  )
}
