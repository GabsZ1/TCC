import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Copy, Eye, Pencil, Plus, Power, RotateCcw, Scissors, Trash2, UserCheck, UsersRound, UserX } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getApiErrorMessage } from '@/lib/apiError'
import {
  createEmployee,
  deactivateEmployee,
  fetchEmployees,
  removeEmployee,
  reactivateEmployee,
  updateEmployee,
  type Employee,
  type EmployeeCreateInput,
  type EmployeeUpdateInput,
} from '@/features/employees/services/employeesApi'
import { EmployeeFormModal } from '@/features/employees/components/EmployeeFormModal'
import { EmployeeAvatar } from '@/features/employees/components/EmployeeAvatar'
import { EmployeeDetailsModal } from '@/features/employees/components/EmployeeDetailsModal'
import { ProfessionalServicesModal } from '@/features/employees/components/ProfessionalServicesModal'

const EMPLOYEES_QUERY_KEY = ['employees']
type ModalState = { mode: 'create' } | { mode: 'edit'; employee: Employee } | null

export function EmployeesListPage() {
  const queryClient = useQueryClient()
  const [modalState, setModalState] = useState<ModalState>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [employeeToDeactivate, setEmployeeToDeactivate] = useState<Employee | null>(null)
  const [employeeToDelete, setEmployeeToDelete] = useState<Employee | null>(null)
  const [employeeToPermanentlyConfirm, setEmployeeToPermanentlyConfirm] = useState<Employee | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [removalMessage, setRemovalMessage] = useState<string | null>(null)
  const [servicesEmployee, setServicesEmployee] = useState<Employee | null>(null)
  const [detailsEmployee, setDetailsEmployee] = useState<Employee | null>(null)
  const [inviteCopied, setInviteCopied] = useState(false)
  const [firstAccessInvitation, setFirstAccessInvitation] = useState<{
    employeeName: string
    link: string
    expiresAt: string
  } | null>(null)

  const { data: employees, isLoading, isError, error } = useQuery({
    queryKey: EMPLOYEES_QUERY_KEY,
    queryFn: fetchEmployees,
  })

  const sortedEmployees = useMemo(
    () => [...(employees ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')),
    [employees],
  )
  const activeCount = sortedEmployees.filter((employee) => employee.active).length
  const inactiveCount = sortedEmployees.length - activeCount

  const createMutation = useMutation({
    mutationFn: createEmployee,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
      setFirstAccessInvitation({
        employeeName: result.name,
        link: `${window.location.origin}/primeiro-acesso?token=${encodeURIComponent(result.firstAccessToken)}`,
        expiresAt: result.firstAccessExpiresAt,
      })
      setInviteCopied(false)
      closeFormModal()
    },
    onError: (err) => setFormError(getApiErrorMessage(err, 'Não foi possível criar o funcionário.')),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: EmployeeUpdateInput }) => updateEmployee(id, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
      setRemovalMessage('O funcionário foi alterado com sucesso')
      closeFormModal()
    },
    onError: (err) => setFormError(getApiErrorMessage(err, 'Não foi possível atualizar o funcionário.')),
  })

  const removeMutation = useMutation({
    mutationFn: removeEmployee,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
      setEmployeeToPermanentlyConfirm(null)
      setRemovalMessage(result.message)
    },
    onError: (err) => {
      setEmployeeToPermanentlyConfirm(null)
      setDeleteError(getApiErrorMessage(err, 'Não foi possível excluir o funcionário.'))
    },
  })

  const deactivateMutation = useMutation({
    mutationFn: deactivateEmployee,
    onSuccess: (employee) => {
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
      setEmployeeToDeactivate(null)
      setDeleteError(null)
      setRemovalMessage(`${employee.name} foi desativado com sucesso.`)
    },
    onError: (err) => {
      setEmployeeToDeactivate(null)
      setDeleteError(getApiErrorMessage(err, 'Não foi possível desativar o funcionário.'))
    },
  })

  const reactivateMutation = useMutation({
    mutationFn: reactivateEmployee,
    onSuccess: (employee) => {
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
      setDeleteError(null)
      setRemovalMessage(`${employee.name} foi reativado com sucesso.`)
    },
    onError: (err) => setDeleteError(getApiErrorMessage(err, 'Não foi possível reativar o funcionário.')),
  })

  function closeFormModal() {
    setModalState(null)
    setFormError(null)
  }

  function handleCreate(values: EmployeeCreateInput) {
    setFormError(null)
    createMutation.mutate(values)
  }

  function handleUpdate(values: EmployeeUpdateInput) {
    if (modalState?.mode !== 'edit') return
    setFormError(null)
    updateMutation.mutate({ id: modalState.employee.id, input: values })
  }

  function handleFirstConfirmDelete() {
    if (!employeeToDelete) return
    setEmployeeToPermanentlyConfirm(employeeToDelete)
    setEmployeeToDelete(null)
  }

  function handleFinalConfirmDelete() {
    if (!employeeToPermanentlyConfirm) return
    setDeleteError(null)
    setRemovalMessage(null)
    removeMutation.mutate(employeeToPermanentlyConfirm.id)
  }

  function cancelRemoval() {
    setEmployeeToDelete(null)
    setEmployeeToPermanentlyConfirm(null)
  }

  async function copyInvite() {
    if (!firstAccessInvitation) return
    try {
      await navigator.clipboard.writeText(firstAccessInvitation.link)
      setInviteCopied(true)
      window.setTimeout(() => setInviteCopied(false), 1800)
    } catch {
      // O link continua disponível para cópia manual.
    }
  }

  const isChangingLifecycle = removeMutation.isPending || deactivateMutation.isPending || reactivateMutation.isPending
  const isSavingForm = createMutation.isPending || updateMutation.isPending

  return (
    <div className="page-shell">
      <header className="flex flex-col gap-5 border-b border-background-300 pb-7 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Gestão de equipe</p>
          <h1 className="page-title mt-2">Funcionários</h1>
          <p className="page-subtitle">Gerencie acessos, disponibilidade e os serviços que cada profissional pode realizar.</p>
        </div>
        <button onClick={() => { setRemovalMessage(null); setModalState({ mode: 'create' }) }} className="primary-button w-fit">
          <Plus size={17} /> Novo funcionário
        </button>
      </header>

      <section className="surface-card mt-7 grid overflow-hidden sm:grid-cols-3">
        <MiniMetric icon={<UsersRound size={17} />} label="Total" value={sortedEmployees.length} />
        <MiniMetric icon={<UserCheck size={17} />} label="Ativos" value={activeCount} tone="success" bordered />
        <MiniMetric icon={<UserX size={17} />} label="Inativos" value={inactiveCount} tone="muted" bordered />
      </section>

      {removalMessage && (
        <p role="status" className="mt-5 rounded-xl border border-success-500/20 bg-success-50 px-4 py-3 text-sm font-medium text-success-700">
          {removalMessage}
        </p>
      )}

      {firstAccessInvitation && (
        <section className="mt-5 rounded-[1.15rem] border border-primary-200 bg-primary-50 p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="max-w-2xl">
              <p className="text-sm font-semibold text-text-900">Convite de primeiro acesso criado para {firstAccessInvitation.employeeName}</p>
              <p className="mt-1 text-sm leading-6 text-text-600">Enviamos este link para o e-mail do funcionário. Se preferir, copie e envie por outro canal — ele aparece apenas agora e expira em {new Date(firstAccessInvitation.expiresAt).toLocaleString('pt-BR')}.</p>
            </div>
            <button type="button" onClick={copyInvite} className="secondary-button shrink-0">
              {inviteCopied ? <Check size={16} /> : <Copy size={16} />}
              {inviteCopied ? 'Copiado' : 'Copiar link'}
            </button>
          </div>
          <input aria-label="Link de primeiro acesso" readOnly value={firstAccessInvitation.link} className="field-control mt-4 font-mono text-xs" />
        </section>
      )}

      <section className="surface-card mt-6 overflow-hidden">
        <div className="flex items-center justify-between border-b border-background-300 px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold text-text-900">Equipe cadastrada</h2>
            <p className="mt-0.5 text-xs text-text-500">Ordenada alfabeticamente</p>
          </div>
          <span className="rounded-full bg-background-200 px-2.5 py-1 text-xs font-semibold text-text-600">{sortedEmployees.length} registros</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[52rem] text-left text-sm">
            <thead className="bg-background-100/75 text-[0.67rem] font-bold uppercase tracking-[0.11em] text-text-500">
              <tr>
                <th className="px-5 py-3.5">Profissional</th>
                <th className="px-5 py-3.5">Ocupação</th>
                <th className="px-5 py-3.5">Status</th>
                <th className="px-5 py-3.5 text-right">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-background-300">
              {isLoading && <TableMessage>Carregando funcionários...</TableMessage>}
              {isError && !isLoading && <TableMessage error>{getApiErrorMessage(error, 'Não foi possível carregar os funcionários.')}</TableMessage>}
              {!isLoading && !isError && sortedEmployees.length === 0 && <TableMessage>Nenhum funcionário cadastrado ainda.</TableMessage>}

              {!isLoading && !isError && sortedEmployees.map((employee) => (
                <tr key={employee.id} className="group bg-background-50 hover:bg-background-100/50">
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-3">
                      <EmployeeAvatar id={employee.id} name={employee.name} hasPhoto={employee.hasPhoto} />
                      <div className="min-w-0">
                        <span className="block font-semibold text-text-900">{employee.name}</span>
                        <span className="block truncate text-xs text-text-500">{employee.email}</span>
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-4 text-text-700">{employee.occupation ?? <span className="text-text-400">Não informada</span>}</td>
                  <td className="px-5 py-4">
                    <span className={`status-pill ${employee.active ? 'bg-success-50 text-success-700' : 'bg-background-200 text-text-600'}`}>
                      <span className={`size-1.5 rounded-full ${employee.active ? 'bg-success-500' : 'bg-text-400'}`} />
                      {employee.active ? 'Ativo' : 'Inativo'}
                    </span>
                  </td>
                  <td className="px-5 py-4">
                    <div className="flex justify-end gap-1">
                      <ActionButton label="Detalhes" onClick={() => setDetailsEmployee(employee)}><Eye size={16} /></ActionButton>
                      <ActionButton label="Serviços" onClick={() => setServicesEmployee(employee)}><Scissors size={16} /></ActionButton>
                      <ActionButton label="Editar" onClick={() => { setRemovalMessage(null); setModalState({ mode: 'edit', employee }) }}><Pencil size={16} /></ActionButton>
                      {employee.active ? (
                        <ActionButton label="Desativar" danger disabled={isChangingLifecycle} onClick={() => { setDeleteError(null); setRemovalMessage(null); setEmployeeToDeactivate(employee) }}><Power size={16} /></ActionButton>
                      ) : (
                        <ActionButton label="Reativar" success disabled={isChangingLifecycle} onClick={() => { setDeleteError(null); setRemovalMessage(null); reactivateMutation.mutate(employee.id) }}><RotateCcw size={16} /></ActionButton>
                      )}
                      <ActionButton label="Excluir definitivamente" danger disabled={isChangingLifecycle} onClick={() => { setDeleteError(null); setRemovalMessage(null); setEmployeeToDelete(employee) }}><Trash2 size={16} /></ActionButton>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {deleteError && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{deleteError}</p>}

      {modalState && (
        <EmployeeFormModal employee={modalState.mode === 'edit' ? modalState.employee : null} isSubmitting={isSavingForm} errorMessage={formError} onCreate={handleCreate} onUpdate={handleUpdate} onClose={closeFormModal} />
      )}
      {detailsEmployee && (
        <EmployeeDetailsModal
          employee={detailsEmployee}
          onClose={() => setDetailsEmployee(null)}
          onEdit={() => { setRemovalMessage(null); setModalState({ mode: 'edit', employee: detailsEmployee }); setDetailsEmployee(null) }}
        />
      )}
      {servicesEmployee && <ProfessionalServicesModal employee={servicesEmployee} onClose={() => setServicesEmployee(null)} />}
      {employeeToDeactivate && <ConfirmDialog title="Desativar funcionário" message={`Desativar "${employeeToDeactivate.name}"? O cadastro, os agendamentos e os serviços vinculados serão preservados. Você poderá reativá-lo depois.`} confirmLabel="Desativar" isLoading={deactivateMutation.isPending} onConfirm={() => deactivateMutation.mutate(employeeToDeactivate.id)} onCancel={() => setEmployeeToDeactivate(null)} />}
      {employeeToDelete && <ConfirmDialog title="Excluir funcionário definitivamente" message={`Excluir "${employeeToDelete.name}" definitivamente? A exclusão será recusada se houver agendamentos ou serviços vinculados. Para apenas suspender o cadastro, use Desativar.`} confirmLabel="Continuar" onConfirm={handleFirstConfirmDelete} onCancel={cancelRemoval} />}
      {employeeToPermanentlyConfirm && <ConfirmDialog title="Confirmar remoção definitiva" message={`Esta ação é irreversível: o cadastro de "${employeeToPermanentlyConfirm.name}" será excluído definitivamente se não houver vínculos. Digite o nome completo para confirmar.`} confirmLabel="Excluir definitivamente" confirmationText={employeeToPermanentlyConfirm.name} confirmationLabel="Digite o nome exato do funcionário para confirmar" isLoading={removeMutation.isPending} onConfirm={handleFinalConfirmDelete} onCancel={cancelRemoval} />}
    </div>
  )
}

function MiniMetric({ icon, label, value, tone = 'primary', bordered = false }: { icon: React.ReactNode; label: string; value: number; tone?: 'primary' | 'success' | 'muted'; bordered?: boolean }) {
  const iconStyle = tone === 'success' ? 'text-success-700' : tone === 'muted' ? 'text-text-600' : 'text-primary-700'
  return <div className={`flex items-center gap-3 p-4 sm:p-5 ${bordered ? 'border-t border-background-300 sm:border-l sm:border-t-0' : ''}`}><span className={`grid size-8 place-items-center ${iconStyle}`}>{icon}</span><div><p className="text-xl font-extrabold tracking-[-0.04em] text-text-950">{value}</p><p className="text-xs font-semibold text-text-500">{label}</p></div></div>
}

function ActionButton({ label, onClick, children, danger = false, success = false, disabled = false }: { label: string; onClick: () => void; children: React.ReactNode; danger?: boolean; success?: boolean; disabled?: boolean }) {
  const tone = danger ? 'hover:bg-danger-50 hover:text-danger-600' : success ? 'hover:bg-success-50 hover:text-success-700' : 'hover:bg-primary-50 hover:text-primary-700'
  return <button type="button" disabled={disabled} onClick={onClick} className={`grid size-9 place-items-center rounded-xl text-text-500 ${tone} disabled:cursor-not-allowed disabled:opacity-40`} title={label} aria-label={label}>{children}</button>
}

function TableMessage({ children, error = false }: { children: React.ReactNode; error?: boolean }) {
  return <tr><td colSpan={4} className={`px-5 py-10 text-center text-sm ${error ? 'text-danger-600' : 'text-text-500'}`}>{children}</td></tr>
}
