/** Mostra os dados de um funcionário e as ações administrativas disponíveis. */
import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, IdCard, MapPin, Phone, Scissors } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { EmployeeAvatar } from '@/features/employees/components/EmployeeAvatar'
import { fetchEmployeeServices, type Employee } from '@/features/employees/services/employeesApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatCpf } from '@/lib/cpf'
import { formatDate } from '@/lib/format'
import { formatPhone } from '@/lib/phone'

/** Dados completos do funcionário na visão do administrador. */
export function EmployeeDetailsModal({ employee, onClose, onEdit }: { employee: Employee; onClose: () => void; onEdit: () => void }) {
  const services = useQuery({
    queryKey: ['employee-services', employee.id],
    queryFn: () => fetchEmployeeServices(employee.id),
  })

  return (
    <Modal title="Detalhes do funcionário" onClose={onClose} size="lg">
      <div className="space-y-5 sm:space-y-6">
        <section className="overflow-hidden rounded-2xl border border-background-300 bg-white">
          <div className="h-20 bg-gradient-to-r from-primary-900 via-primary-700 to-secondary-600 sm:h-24" aria-hidden="true" />
          <div className="flex flex-col gap-3 px-4 pb-5 sm:flex-row sm:items-end sm:gap-5 sm:px-6">
            <div className="-mt-10 w-fit rounded-[1.25rem] border-4 border-white bg-white shadow-md sm:-mt-12">
              <EmployeeAvatar id={employee.id} name={employee.name} hasPhoto={employee.hasPhoto} size="xl" />
            </div>
            <div className="min-w-0 flex-1 sm:pb-1">
              <h3 className="break-words text-xl font-extrabold tracking-tight text-text-950 sm:text-2xl">{employee.name}</h3>
              <p className="mt-1 break-words text-sm text-text-600">{employee.occupation || 'Ocupação não informada'}</p>
              {employee.establishmentName && <p className="mt-1 flex items-center gap-1.5 text-xs text-text-500"><MapPin size={13} /> {employee.establishmentName}</p>}
            </div>
            <span className={`status-pill self-start sm:mb-1 ${employee.active ? 'bg-success-50 text-success-700' : 'bg-background-200 text-text-600'}`}>
              <span className={`size-1.5 rounded-full ${employee.active ? 'bg-success-500' : 'bg-text-400'}`} />
              {employee.active ? 'Ativo' : 'Desativado'}
            </span>
          </div>
        </section>

        <div className="grid gap-4 md:grid-cols-2">
          <section className="rounded-2xl border border-background-300 bg-white p-4 sm:p-5">
            <SectionTitle icon={<Phone size={18} />} title="Contato" />
            <dl className="mt-4 space-y-4">
              <InfoField label="E-mail" value={<a className="break-all text-primary-700 hover:underline" href={`mailto:${employee.email}`}>{employee.email}</a>} />
              <InfoField label="Telefone" value={employee.phone ? <a className="text-primary-700 hover:underline" href={`tel:${employee.phone.replace(/\D/g, '')}`}>{formatPhone(employee.phone)}</a> : null} />
              <InfoField label="Instagram" value={employee.instagram ? <a className="break-all text-primary-700 hover:underline" href={`https://instagram.com/${employee.instagram.replace(/^@/, '')}`} target="_blank" rel="noopener noreferrer">@{employee.instagram.replace(/^@/, '')}</a> : null} />
              <InfoField label="Endereço" value={employee.address} />
            </dl>
          </section>

          <section className="rounded-2xl border border-background-300 bg-white p-4 sm:p-5">
            <SectionTitle icon={<IdCard size={18} />} title="Cadastro" />
            <dl className="mt-4 space-y-4">
              <InfoField label="CPF" value={employee.cpf ? formatCpf(employee.cpf) : null} />
              <InfoField label="Data de admissão" value={employee.hireDate ? formatDate(employee.hireDate) : null} />
              <InfoField label="Tipo de acesso" value={roleLabel(employee.role)} />
              <InfoField label="Primeiro acesso" value={employee.passwordInitialized ? 'Concluído' : 'Pendente'} />
            </dl>
          </section>
        </div>

        <section className="rounded-2xl border border-background-300 bg-white p-4 sm:p-5">
          <SectionTitle icon={<Scissors size={18} />} title="Serviços vinculados" />
          {services.isLoading && <p className="mt-4 text-sm text-text-500">Carregando serviços...</p>}
          {services.isError && <p role="alert" className="mt-4 text-sm text-danger-600">{getApiErrorMessage(services.error, 'Não foi possível carregar os serviços.')}</p>}
          {services.data?.allServices && <p className="mt-4 flex items-center gap-2 text-sm font-medium text-text-700"><CheckCircle2 size={16} className="text-success-600" /> Todos os serviços ativos, inclusive os cadastrados futuramente.</p>}
          {services.data && !services.data.allServices && (
            services.data.services.length > 0
              ? <ul className="mt-4 flex flex-wrap gap-2">{services.data.services.map((service) => <li key={service.id} className="rounded-xl bg-background-100 px-3 py-2 text-sm font-medium text-text-700">{service.name}</li>)}</ul>
              : <p className="mt-4 text-sm text-text-500">Nenhum serviço vinculado.</p>
          )}
        </section>

        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button type="button" className="secondary-button justify-center" onClick={onClose}>Fechar</button>
          <button type="button" className="primary-button justify-center" onClick={onEdit}>Editar funcionário</button>
        </div>
      </div>
    </Modal>
  )
}

function SectionTitle({ icon, title }: { icon: React.ReactNode; title: string }) {
  return <h4 className="flex items-center gap-2 font-semibold text-text-950"><span className="text-primary-600">{icon}</span>{title}</h4>
}

function InfoField({ label, value }: { label: string; value: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-xs font-medium text-text-500">{label}</dt><dd className="mt-1 break-words text-sm font-semibold leading-5 text-text-900">{value || <span className="font-normal text-text-500">Não informado</span>}</dd></div>
}

function roleLabel(role: Employee['role']) {
  return ({ PROFESSIONAL: 'Profissional', ADMIN: 'Administrador', SYSTEM_ADMIN: 'Administrador do sistema', CLIENT: 'Cliente' })[role]
}
