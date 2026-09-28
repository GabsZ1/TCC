import { useEffect, useMemo, useRef, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { IMaskInput } from 'react-imask'
import { z } from 'zod'
import { ImagePlus, Trash2 } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { EmployeeAvatar } from '@/features/employees/components/EmployeeAvatar'
import type { Employee, EmployeeInput } from '@/features/employees/services/employeesApi'
import { formatCpf, isValidCpf } from '@/lib/cpf'
import { todayIso } from '@/lib/format'
import { formatPhone, isValidPhone, PHONE_MASK } from '@/lib/phone'

const OCCUPATIONS = [
  'Cabeleireiro(a)', 'Barbeiro(a)', 'Manicure', 'Pedicure', 'Esteticista', 'Maquiador(a)',
  'Designer de sobrancelhas', 'Depilador(a)', 'Massoterapeuta', 'Recepcionista',
]
const MAX_PHOTO_BYTES = 2 * 1024 * 1024
const ACCEPTED_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const employeeSchema = z.object({
  name: z.string().trim().min(1, 'Nome é um campo obrigatório!').max(150),
  email: z.string().trim().min(1, 'Email é um campo obrigatório!').email('Email inválido'),
  occupation: z.string().trim().min(1, 'Ocupação é um campo obrigatório!').max(80),
  cpf: z.string().trim().min(1, 'CPF é um campo obrigatório!').refine(isValidCpf, 'CPF inválido'),
  phone: z.string().trim().refine((value) => value === '' || isValidPhone(value), 'Informe um telefone válido!'),
  instagram: z.string().trim().refine((value) => value === '' || /^@?[A-Za-z0-9._]{1,30}$/.test(value), 'Use até 30 letras, números, ponto ou sublinhado'),
  address: z.string().trim().max(255, 'Máximo de 255 caracteres'),
  hireDate: z.string().refine((value) => value === '' || value <= todayIso(), 'A data de admissão não pode ser futura'),
})

export type EmployeeFormValues = z.infer<typeof employeeSchema>

/** undefined = foto sem alteração; null = remover; File = nova foto. */
export type EmployeePhotoChange = File | null | undefined

interface EmployeeFormModalProps {
  employee?: Employee | null
  isSubmitting: boolean
  errorMessage?: string | null
  onSubmit: (values: EmployeeInput, photo: EmployeePhotoChange) => void
  onClose: () => void
}

export function EmployeeFormModal({ employee, isSubmitting, errorMessage, onSubmit, onClose }: EmployeeFormModalProps) {
  const isEditing = !!employee
  const [photo, setPhoto] = useState<EmployeePhotoChange>(undefined)
  const [photoError, setPhotoError] = useState<string | null>(null)
  const [pendingValues, setPendingValues] = useState<EmployeeInput | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const { register, control, handleSubmit, formState: { errors } } = useForm<EmployeeFormValues>({
    resolver: zodResolver(employeeSchema),
    defaultValues: {
      name: employee?.name ?? '',
      email: employee?.email ?? '',
      occupation: employee?.occupation ?? '',
      cpf: formatCpf(employee?.cpf),
      phone: employee?.phone ? formatPhone(employee.phone) : '',
      instagram: employee?.instagram ? `@${employee.instagram}` : '',
      address: employee?.address ?? '',
      hireDate: employee?.hireDate ?? '',
    },
  })

  const previewUrl = useMemo(() => (photo instanceof File ? URL.createObjectURL(photo) : null), [photo])
  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl) }, [previewUrl])

  function selectPhoto(file: File | undefined) {
    setPhotoError(null)
    if (!file) return
    if (!ACCEPTED_PHOTO_TYPES.includes(file.type)) return setPhotoError('Use uma imagem JPG, PNG ou WEBP.')
    if (file.size > MAX_PHOTO_BYTES) return setPhotoError('A foto deve ter no máximo 2 MB.')
    setPhoto(file)
  }

  function submit(values: EmployeeFormValues) {
    const input: EmployeeInput = {
      name: values.name.trim(),
      email: values.email.trim(),
      occupation: values.occupation.trim(),
      cpf: values.cpf,
      phone: values.phone || undefined,
      instagram: values.instagram || undefined,
      address: values.address || undefined,
      hireDate: values.hireDate || undefined,
    }
    // US 2.2: a edição pede confirmação antes de salvar.
    if (isEditing) setPendingValues(input)
    else onSubmit(input, photo)
  }

  const showsStoredPhoto = photo === undefined && !!employee?.hasPhoto
  const hasAnyPhoto = previewUrl !== null || showsStoredPhoto

  if (pendingValues) {
    return (
      <Modal title="Confirmar alterações" onClose={() => setPendingValues(null)}>
        <p className="text-sm leading-6 text-text-700">
          Deseja salvar as alterações de <strong>{pendingValues.name}</strong>? O histórico de atendimentos do funcionário será mantido.
        </p>
        {errorMessage && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}
        <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button type="button" className="secondary-button" onClick={() => setPendingValues(null)}>Voltar</button>
          <button type="button" className="primary-button" disabled={isSubmitting} onClick={() => onSubmit(pendingValues, photo)}>
            {isSubmitting ? 'Salvando...' : 'Confirmar'}
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title={isEditing ? 'Editar funcionário' : 'Novo funcionário'} onClose={onClose} size="lg">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <p className="mb-5 text-sm leading-6 text-text-600">
          {isEditing ? 'Atualize os dados do profissional.' : 'Cadastre o profissional. Ele receberá por e-mail o link para definir a senha.'}
          {' '}Campos com <span className="font-bold text-danger-600">*</span> são obrigatórios.
        </p>

        <div className="mb-6 flex items-center gap-4">
          {previewUrl
            ? <img src={previewUrl} alt="Pré-visualização da foto" className="size-20 shrink-0 rounded-2xl object-cover" />
            : employee && showsStoredPhoto
              ? <EmployeeAvatar id={employee.id} name={employee.name} hasPhoto size="lg" />
              : <span className="grid size-20 shrink-0 place-items-center rounded-2xl border border-dashed border-background-400 text-text-400"><ImagePlus size={24} /></span>}
          <div>
            <p className="text-sm font-semibold text-text-800">Foto</p>
            <p className="text-xs text-text-500">JPG, PNG ou WEBP, até 2 MB.</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <button type="button" className="secondary-button px-3 py-1.5 text-xs" onClick={() => fileInput.current?.click()}>
                {hasAnyPhoto ? 'Trocar foto' : 'Escolher foto'}
              </button>
              {hasAnyPhoto && (
                <button type="button" className="inline-flex items-center gap-1 rounded-lg px-2 py-1.5 text-xs font-semibold text-danger-600 hover:bg-danger-50" onClick={() => { setPhoto(employee?.hasPhoto ? null : undefined); setPhotoError(null) }}>
                  <Trash2 size={13} /> Remover
                </button>
              )}
            </div>
            <input ref={fileInput} type="file" accept={ACCEPTED_PHOTO_TYPES.join(',')} className="hidden" onChange={(event) => { selectPhoto(event.target.files?.[0]); event.target.value = '' }} />
            {photoError && <p className="mt-1.5 text-xs text-danger-600">{photoError}</p>}
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Nome" required error={errors.name?.message} className="sm:col-span-2">
            <input type="text" autoFocus {...register('name')} className={inputClass(errors.name)} placeholder="Nome completo" />
          </Field>
          <Field label="E-mail" required error={errors.email?.message}>
            <input type="email" {...register('email')} className={inputClass(errors.email)} placeholder="profissional@exemplo.com" />
          </Field>
          <Field label="Ocupação" required error={errors.occupation?.message}>
            <input list="employee-occupations" {...register('occupation')} className={inputClass(errors.occupation)} placeholder="Ex.: Cabeleireiro(a)" />
            <datalist id="employee-occupations">{OCCUPATIONS.map((occupation) => <option key={occupation} value={occupation} />)}</datalist>
          </Field>
          <Field label="CPF" required error={errors.cpf?.message}>
            <Controller name="cpf" control={control} render={({ field }) => (
              <IMaskInput mask="000.000.000-00" value={field.value} onAccept={(value: string) => field.onChange(value)} onBlur={field.onBlur} inputMode="numeric" placeholder="000.000.000-00" className={inputClass(errors.cpf)} />
            )} />
          </Field>
          <Field label="Telefone" error={errors.phone?.message}>
            <Controller name="phone" control={control} render={({ field }) => (
              <IMaskInput mask={PHONE_MASK} value={field.value} onAccept={(value: string) => field.onChange(value)} onBlur={field.onBlur} inputMode="tel" placeholder="(00) 00000-0000" className={inputClass(errors.phone)} />
            )} />
          </Field>
          <Field label="Instagram" error={errors.instagram?.message}>
            <input {...register('instagram')} className={inputClass(errors.instagram)} placeholder="@perfil" />
          </Field>
          <Field label="Data de admissão" error={errors.hireDate?.message}>
            <input type="date" max={todayIso()} {...register('hireDate')} className={inputClass(errors.hireDate)} />
          </Field>
          <Field label="Endereço" error={errors.address?.message} className="sm:col-span-2">
            <input {...register('address')} className={inputClass(errors.address)} placeholder="Rua, número, bairro e cidade" />
          </Field>
        </div>

        {errorMessage && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}

        <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button type="button" onClick={onClose} className="secondary-button">Cancelar</button>
          <button type="submit" disabled={isSubmitting} className="primary-button">{isSubmitting ? 'Salvando...' : 'Salvar funcionário'}</button>
        </div>
      </form>
    </Modal>
  )
}

function inputClass(error?: { message?: string }) {
  // RNF.01.02 da US 2.2: campo com erro fica destacado em vermelho, com o aviso logo abaixo.
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
