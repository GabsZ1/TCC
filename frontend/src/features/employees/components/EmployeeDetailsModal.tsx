import { Modal } from '@/components/ui/Modal'
import { EmployeeAvatar } from '@/features/employees/components/EmployeeAvatar'
import type { Employee } from '@/features/employees/services/employeesApi'
import { formatCpf } from '@/lib/cpf'
import { formatDate } from '@/lib/format'
import { formatPhone } from '@/lib/phone'

/** US 2.6 — visualizar os detalhes de um funcionário selecionado (somente leitura). */
export function EmployeeDetailsModal({ employee, onClose, onEdit }: { employee: Employee; onClose: () => void; onEdit: () => void }) {
  const rows: Array<[string, string]> = [
    ['E-mail', employee.email],
    ['Ocupação', employee.occupation ?? 'Não informada'],
    ['CPF', employee.cpf ? formatCpf(employee.cpf) : 'Não informado'],
    ['Telefone', employee.phone ? formatPhone(employee.phone) : 'Não informado'],
    ['Instagram', employee.instagram ? `@${employee.instagram}` : 'Não informado'],
    ['Endereço', employee.address ?? 'Não informado'],
    ['Data de admissão', employee.hireDate ? formatDate(employee.hireDate) : 'Não informada'],
    ['Primeiro acesso', employee.passwordInitialized ? 'Concluído' : 'Pendente (convite enviado por e-mail)'],
  ]

  return (
    <Modal title="Detalhes do funcionário" onClose={onClose}>
      <div className="flex items-center gap-4">
        <EmployeeAvatar id={employee.id} name={employee.name} hasPhoto={employee.hasPhoto} size="lg" />
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold text-text-900">{employee.name}</p>
          <span className={`status-pill mt-1 ${employee.active ? 'bg-success-50 text-success-700' : 'bg-background-200 text-text-600'}`}>
            <span className={`size-1.5 rounded-full ${employee.active ? 'bg-success-500' : 'bg-text-400'}`} />
            {employee.active ? 'Ativo' : 'Inativo'}
          </span>
        </div>
      </div>
      <dl className="mt-5 divide-y divide-background-300 rounded-xl border border-background-300">
        {rows.map(([label, value]) => (
          <div key={label} className="flex gap-3 px-4 py-2.5 text-sm">
            <dt className="w-36 shrink-0 text-text-500">{label}</dt>
            <dd className="min-w-0 break-words font-medium text-text-900">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" className="secondary-button" onClick={onClose}>Fechar</button>
        <button type="button" className="primary-button" onClick={onEdit}>Editar</button>
      </div>
    </Modal>
  )
}
