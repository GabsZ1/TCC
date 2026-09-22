import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Modal } from '@/components/ui/Modal'
import type { Employee } from '@/features/employees/services/employeesApi'

const employeeSchema = z.object({
  name: z.string().trim().min(1, 'O nome é obrigatório'),
  email: z.string().trim().min(1, 'O e-mail é obrigatório').email('E-mail em formato inválido'),
})

export type EmployeeFormValues = z.infer<typeof employeeSchema>

interface EmployeeFormModalProps {
  employee?: Employee | null
  isSubmitting: boolean
  errorMessage?: string | null
  onSubmit: (values: EmployeeFormValues) => void
  onClose: () => void
}

export function EmployeeFormModal({ employee, isSubmitting, errorMessage, onSubmit, onClose }: EmployeeFormModalProps) {
  const isEditing = !!employee
  const { register, handleSubmit, formState: { errors } } = useForm<EmployeeFormValues>({
    resolver: zodResolver(employeeSchema),
    defaultValues: { name: employee?.name ?? '', email: employee?.email ?? '' },
  })

  return (
    <Modal title={isEditing ? 'Editar funcionário' : 'Novo funcionário'} onClose={onClose}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <p className="mb-5 text-sm leading-6 text-text-600">
          {isEditing ? 'Atualize os dados de acesso do profissional.' : 'Cadastre o profissional. Depois você poderá definir quais serviços ele realiza.'}
        </p>

        <label className="block text-sm font-semibold text-text-800" htmlFor="employee-name">Nome</label>
        <input id="employee-name" type="text" autoFocus {...register('name')} className="field-control" placeholder="Nome completo" />
        {errors.name && <p className="mt-1.5 text-sm text-danger-600">{errors.name.message}</p>}

        <label className="mt-5 block text-sm font-semibold text-text-800" htmlFor="employee-email">E-mail</label>
        <input id="employee-email" type="email" {...register('email')} className="field-control" placeholder="profissional@exemplo.com" />
        {errors.email && <p className="mt-1.5 text-sm text-danger-600">{errors.email.message}</p>}

        {errorMessage && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}

        <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button type="button" onClick={onClose} className="secondary-button">Cancelar</button>
          <button type="submit" disabled={isSubmitting} className="primary-button">{isSubmitting ? 'Salvando...' : 'Salvar funcionário'}</button>
        </div>
      </form>
    </Modal>
  )
}
