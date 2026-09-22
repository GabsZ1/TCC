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
        <label className="block text-sm font-medium text-text-800" htmlFor="employee-name">
          Nome
        </label>
        <input
          id="employee-name"
          type="text"
          autoFocus
          {...register('name')}
          className="mt-1 w-full rounded-lg border border-background-300 px-3 py-2 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
        />
        {errors.name && <p className="mt-1 text-sm text-danger-600">{errors.name.message}</p>}

        <label className="mt-4 block text-sm font-medium text-text-800" htmlFor="employee-email">
          E-mail
        </label>
        <input
          id="employee-email"
          type="email"
          {...register('email')}
          className="mt-1 w-full rounded-lg border border-background-300 px-3 py-2 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
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
            className="rounded-lg border border-background-300 px-4 py-2 text-sm font-medium text-text-700 hover:bg-background-100"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-700 disabled:opacity-60"
          >
            {isSubmitting ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
