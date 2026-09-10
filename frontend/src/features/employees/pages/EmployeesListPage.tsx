import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getApiErrorMessage } from '@/lib/apiError'
import {
  createEmployee,
  deactivateEmployee,
  fetchEmployees,
  updateEmployee,
  type Employee,
} from '@/features/employees/services/employeesApi'
import {
  EmployeeFormModal,
  type EmployeeFormValues,
} from '@/features/employees/components/EmployeeFormModal'

const EMPLOYEES_QUERY_KEY = ['employees']

type ModalState = { mode: 'create' } | { mode: 'edit'; employee: Employee } | null

export function EmployeesListPage() {
  const queryClient = useQueryClient()

  const [modalState, setModalState] = useState<ModalState>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [employeeToDelete, setEmployeeToDelete] = useState<Employee | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const {
    data: employees,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: EMPLOYEES_QUERY_KEY,
    queryFn: fetchEmployees,
  })

  // RN: lista sempre ordenada alfabeticamente por nome.
  const sortedEmployees = useMemo(
    () => [...(employees ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')),
    [employees],
  )

  const createMutation = useMutation({
    mutationFn: createEmployee,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
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

  const deactivateMutation = useMutation({
    mutationFn: deactivateEmployee,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
      setEmployeeToDelete(null)
    },
    onError: (err) =>
      setDeleteError(getApiErrorMessage(err, 'Não foi possível remover o funcionário.')),
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
        password: values.password ?? '',
      })
    }
  }

  function handleConfirmDelete() {
    if (!employeeToDelete) return
    setDeleteError(null)
    deactivateMutation.mutate(employeeToDelete.id)
  }

  const isSavingForm = createMutation.isPending || updateMutation.isPending

  return (
    <div className="p-6">
      <div className="flex items-center justify-between">
        <h1 className="font-[var(--font-display)] text-2xl text-brand-900">Funcionários</h1>
        <button
          onClick={() => setModalState({ mode: 'create' })}
          className="flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
        >
          <Plus size={16} />
          Novo funcionário
        </button>
      </div>

      <div className="mt-6 overflow-hidden rounded-2xl border border-brand-100 bg-white shadow-sm">
        <table className="w-full text-left text-sm">
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
                    <span className="inline-flex items-center rounded-full bg-sage-50 px-2.5 py-1 text-xs font-medium text-sage-600">
                      Ativo
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-3">
                      <button
                        onClick={() => setModalState({ mode: 'edit', employee })}
                        className="flex items-center gap-1 text-brand-600 hover:text-brand-800"
                        aria-label={`Editar ${employee.name}`}
                      >
                        <Pencil size={16} />
                        Editar
                      </button>
                      <button
                        onClick={() => {
                          setDeleteError(null)
                          setEmployeeToDelete(employee)
                        }}
                        className="flex items-center gap-1 text-danger-600 hover:text-danger-500"
                        aria-label={`Remover ${employee.name}`}
                      >
                        <Trash2 size={16} />
                        Remover
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

      {employeeToDelete && (
        <ConfirmDialog
          title="Remover funcionário"
          message={`Tem certeza que deseja remover "${employeeToDelete.name}"? Essa ação inativa o acesso do funcionário ao sistema.`}
          confirmLabel="Remover"
          isLoading={deactivateMutation.isPending}
          onConfirm={handleConfirmDelete}
          onCancel={() => setEmployeeToDelete(null)}
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
