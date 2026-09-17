import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Power, RotateCcw, Scissors, Trash2 } from 'lucide-react'
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
} from '@/features/employees/services/employeesApi'
import {
  EmployeeFormModal,
  type EmployeeFormValues,
} from '@/features/employees/components/EmployeeFormModal'
import { ProfessionalServicesModal } from '@/features/employees/components/ProfessionalServicesModal'

const EMPLOYEES_QUERY_KEY = ['employees']

type ModalState = { mode: 'create' } | { mode: 'edit'; employee: Employee } | null

export function EmployeesListPage() {
  const queryClient = useQueryClient()

  const [modalState, setModalState] = useState<ModalState>(null)
  const [formError, setFormError] = useState<string | null>(null)
  // A exclusão definitiva exige aviso e confirmação pelo nome.
  const [employeeToDeactivate, setEmployeeToDeactivate] = useState<Employee | null>(null)
  const [employeeToDelete, setEmployeeToDelete] = useState<Employee | null>(null)
  const [employeeToPermanentlyConfirm, setEmployeeToPermanentlyConfirm] = useState<Employee | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [removalMessage, setRemovalMessage] = useState<string | null>(null)
  const [servicesEmployee, setServicesEmployee] = useState<Employee | null>(null)
  const [firstAccessInvitation, setFirstAccessInvitation] = useState<{
    employeeName: string
    link: string
    expiresAt: string
  } | null>(null)

  const {
    data: employees,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: EMPLOYEES_QUERY_KEY,
    queryFn: fetchEmployees,
  })

  // RN: lista sempre ordenada alfabeticamente por nome (ativos e inativos aparecem juntos).
  const sortedEmployees = useMemo(
    () => [...(employees ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')),
    [employees],
  )

  const createMutation = useMutation({
    mutationFn: createEmployee,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
      setFirstAccessInvitation({
        employeeName: result.name,
        link: `${window.location.origin}/primeiro-acesso?token=${encodeURIComponent(result.firstAccessToken)}`,
        expiresAt: result.firstAccessExpiresAt,
      })
      closeFormModal()
    },
    onError: (err) => setFormError(getApiErrorMessage(err, 'Não foi possível criar o funcionário.')),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, name, email }: { id: number; name: string; email: string }) =>
      updateEmployee(id, { name, email }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
      closeFormModal()
    },
    onError: (err) =>
      setFormError(getApiErrorMessage(err, 'Não foi possível atualizar o funcionário.')),
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
    onError: (err) =>
      setDeleteError(getApiErrorMessage(err, 'Não foi possível reativar o funcionário.')),
  })

  function closeFormModal() {
    setModalState(null)
    setFormError(null)
  }

  function handleFormSubmit(values: EmployeeFormValues) {
    setFormError(null)
    if (modalState?.mode === 'edit') {
      updateMutation.mutate({ id: modalState.employee.id, name: values.name, email: values.email })
    } else {
      createMutation.mutate({
        name: values.name,
        email: values.email,
      })
    }
  }

  // Primeira confirmação: aviso geral. Ao aceitar, avançamos para a segunda
  // etapa em vez de remover na hora.
  function handleFirstConfirmDelete() {
    if (!employeeToDelete) return
    setEmployeeToPermanentlyConfirm(employeeToDelete)
    setEmployeeToDelete(null)
  }

  // Segunda confirmação: só dispara a remoção de fato depois que o
  // administrador digita o nome exato do funcionário.
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

  const isChangingLifecycle = removeMutation.isPending || deactivateMutation.isPending || reactivateMutation.isPending
  const isSavingForm = createMutation.isPending || updateMutation.isPending

  return (
    <div className="p-6">
      <div className="flex items-center justify-between">
        <h1 className="font-[var(--font-display)] text-2xl text-brand-900">Funcionários</h1>
        <button
          onClick={() => {
            setRemovalMessage(null)
            setModalState({ mode: 'create' })
          }}
          className="flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
        >
          <Plus size={16} />
          Novo funcionário
        </button>
      </div>

      {removalMessage && (
        <p role="status" className="mt-4 rounded-xl bg-sage-50 px-3 py-2 text-sm text-sage-600">
          {removalMessage}
        </p>
      )}

      {firstAccessInvitation && (
        <div className="mt-4 rounded-xl bg-sage-50 p-4 text-sm text-brand-800">
          <p className="font-medium">Funcionário cadastrado. Envie este link de primeiro acesso para {firstAccessInvitation.employeeName}:</p>
          <input
            aria-label="Link de primeiro acesso"
            readOnly
            value={firstAccessInvitation.link}
            className="mt-2 w-full rounded-lg border border-brand-200 bg-white px-3 py-2"
          />
          <p className="mt-2 text-brand-600">O link é exibido apenas neste cadastro e expira em {new Date(firstAccessInvitation.expiresAt).toLocaleString('pt-BR')}.</p>
        </div>
      )}

      <div className="mt-6 overflow-x-auto rounded-2xl border border-brand-100 bg-white shadow-sm">
        <table className="w-full min-w-[44rem] text-left text-sm">
          <thead className="bg-sand-100 text-brand-700">
            <tr>
              <th className="px-4 py-3 font-medium">Nome</th>
              <th className="px-4 py-3 font-medium">E-mail</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Ações</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-brand-500" colSpan={4}>
                  Carregando funcionários...
                </td>
              </tr>
            )}

            {isError && !isLoading && (
              <tr>
                <td className="px-4 py-6 text-danger-600" colSpan={4}>
                  {getApiErrorMessage(error, 'Não foi possível carregar os funcionários.')}
                </td>
              </tr>
            )}

            {!isLoading && !isError && sortedEmployees.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-brand-500" colSpan={4}>
                  Nenhum funcionário cadastrado ainda.
                </td>
              </tr>
            )}

            {!isLoading &&
              !isError &&
              sortedEmployees.map((employee) => (
                <tr key={employee.id} className="border-t border-brand-100">
                  <td className="px-4 py-3 text-brand-900">{employee.name}</td>
                  <td className="px-4 py-3 text-brand-700">{employee.email}</td>
                  <td className="px-4 py-3">
                    {employee.active ? (
                      <span className="inline-flex items-center rounded-full bg-sage-50 px-2.5 py-1 text-xs font-medium text-sage-600">
                        Ativo
                      </span>
                    ) : (
                      <span className="inline-flex items-center rounded-full bg-brand-100 px-2.5 py-1 text-xs font-medium text-brand-600">
                        Inativo
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-3">
                      <button
                        type="button"
                        onClick={() => setServicesEmployee(employee)}
                        className="flex items-center gap-1 text-brand-600 hover:text-brand-800"
                        aria-label={`Gerenciar serviços de ${employee.name}`}
                      >
                        <Scissors size={16} />
                        Serviços
                      </button>
                      <button
                        onClick={() => {
                          setRemovalMessage(null)
                          setModalState({ mode: 'edit', employee })
                        }}
                        className="flex items-center gap-1 text-brand-600 hover:text-brand-800"
                        aria-label={`Editar ${employee.name}`}
                      >
                        <Pencil size={16} />
                        Editar
                      </button>
                      {employee.active && (
                        <button disabled={isChangingLifecycle}
                          onClick={() => {
                            setDeleteError(null)
                            setRemovalMessage(null)
                            setEmployeeToDeactivate(employee)
                          }}
                          className="flex items-center gap-1 text-danger-600 hover:text-danger-500"
                          aria-label={`Desativar ${employee.name}`}
                        >
                          <Power size={16} />
                          Desativar
                        </button>
                      )}
                      {!employee.active && (
                        <button
                          type="button"
                          disabled={isChangingLifecycle}
                          onClick={() => {
                            setDeleteError(null)
                            setRemovalMessage(null)
                            reactivateMutation.mutate(employee.id)
                          }}
                          className="flex items-center gap-1 text-sage-600 hover:text-sage-500 disabled:cursor-not-allowed disabled:opacity-60"
                          aria-label={`Reativar ${employee.name}`}
                        >
                          <RotateCcw size={16} />
                          Reativar
                        </button>
                      )}
                      <button
                        type="button"
                        disabled={isChangingLifecycle}
                        onClick={() => {
                          setDeleteError(null)
                          setRemovalMessage(null)
                          setEmployeeToDelete(employee)
                        }}
                        className="flex items-center gap-1 text-danger-600 hover:text-danger-500 disabled:opacity-60"
                        aria-label={`Excluir definitivamente ${employee.name}`}
                      >
                        <Trash2 size={16} />
                        Excluir definitivamente
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {modalState && (
        <EmployeeFormModal
          employee={modalState.mode === 'edit' ? modalState.employee : null}
          isSubmitting={isSavingForm}
          errorMessage={formError}
          onSubmit={handleFormSubmit}
          onClose={closeFormModal}
        />
      )}

      {servicesEmployee && <ProfessionalServicesModal employee={servicesEmployee} onClose={() => setServicesEmployee(null)} />}

      {employeeToDeactivate && (
        <ConfirmDialog
          title="Desativar funcionário"
          message={`Desativar "${employeeToDeactivate.name}"? O cadastro, os agendamentos e os serviços vinculados serão preservados. Você poderá reativá-lo depois.`}
          confirmLabel="Desativar"
          isLoading={deactivateMutation.isPending}
          onConfirm={() => deactivateMutation.mutate(employeeToDeactivate.id)}
          onCancel={() => setEmployeeToDeactivate(null)}
        />
      )}

      {employeeToDelete && (
        <ConfirmDialog
          title="Excluir funcionário definitivamente"
          message={`Excluir "${employeeToDelete.name}" definitivamente? A exclusão será recusada se houver agendamentos ou serviços vinculados. Para apenas suspender o cadastro, use Desativar.`}
          confirmLabel="Continuar"
          onConfirm={handleFirstConfirmDelete}
          onCancel={cancelRemoval}
        />
      )}

      {employeeToPermanentlyConfirm && (
        <ConfirmDialog
          title="Confirmar remoção definitiva"
          message={`Esta ação é irreversível: o cadastro de "${employeeToPermanentlyConfirm.name}" será excluído definitivamente se não houver vínculos. Digite o nome completo para confirmar.`}
          confirmLabel="Excluir definitivamente"
          confirmationText={employeeToPermanentlyConfirm.name}
          confirmationLabel="Digite o nome exato do funcionário para confirmar"
          isLoading={removeMutation.isPending}
          onConfirm={handleFinalConfirmDelete}
          onCancel={cancelRemoval}
        />
      )}

      {deleteError && (
        <p role="alert" className="mt-4 text-sm text-danger-600">
          {deleteError}
        </p>
      )}
    </div>
  )
}
