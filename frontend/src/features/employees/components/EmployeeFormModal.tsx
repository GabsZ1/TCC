import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Modal } from '@/components/ui/Modal'
import type { Employee } from '@/features/employees/services/employeesApi'

const employeeSchema = z.object({
  name: z.string().trim().min(1, 'O nome é obrigatório'),
  email: z
    .string()
    .trim()
    .min(1, 'O e-mail é obrigatório')
    .email('E-mail em formato inválido'),
})

export type EmployeeFormValues = z.infer<typeof employeeSchema>

interface EmployeeFormModalProps {
  /** Quando informado, o formulário abre em modo de edição. */
  employee?: Employee | null
  isSubmitting: boolean
  errorMessage?: string | null
  onSubmit: (values: EmployeeFormValues) => void
  onClose: () => void
}

export function EmployeeFormModal({
  employee,
  isSubmitting,
  errorMessage,
  onSubmit,
  onClose,
}: EmployeeFormModalProps) {
  const isEditing = !!employee

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<EmployeeFormValues>({
    resolver: zodResolver(employeeSchema),
    defaultValues: {
      name: employee?.name ?? '',
      email: employee?.email ?? '',
    },
  })

  return (
    <Modal title={isEditing ? 'Editar funcionário' : 'Novo funcionário'} onClose={onClose}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <label className="block text-sm font-medium text-brand-800" htmlFor="employee-name">
          Nome
        </label>
        <input
          id="employee-name"
          type="text"
          autoFocus
          {...register('name')}
          className="mt-1 w-full rounded-lg border border-brand-200 px-3 py-2 text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
        />
        {errors.name && <p className="mt-1 text-sm text-danger-600">{errors.name.message}</p>}

        <label className="mt-4 block text-sm font-medium text-brand-800" htmlFor="employee-email">
          E-mail
        </label>
        <input
          id="employee-email"
          type="email"
          {...register('email')}
          className="mt-1 w-full rounded-lg border border-brand-200 px-3 py-2 text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
        />
        {errors.email && <p className="mt-1 text-sm text-danger-600">{errors.email.message}</p>}

        {errorMessage && (
          <p role="alert" className="mt-4 text-sm text-danger-600">
            {errorMessage}
          </p>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-brand-200 px-4 py-2 text-sm font-medium text-brand-700 hover:bg-sand-100"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-60"
          >
            {isSubmitting ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
