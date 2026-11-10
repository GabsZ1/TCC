/** Associa os serviços que cada profissional pode oferecer. */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Layers3 } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { fetchServices, type SalonService } from '@/features/services/servicesApi'
import { fetchEmployeeServices, updateEmployeeServices, type Employee, type ProfessionalServices } from '@/features/employees/services/employeesApi'
import { getApiErrorMessage } from '@/lib/apiError'

/** Consulta os serviços disponíveis antes de abrir a seleção para o profissional. */
export function ProfessionalServicesModal({ employee, onClose }: { employee: Employee; onClose: () => void }) {
  const queryClient = useQueryClient()
  const catalog = useQuery({ queryKey: ['services'], queryFn: fetchServices })
  const assigned = useQuery({ queryKey: ['employee-services', employee.id], queryFn: () => fetchEmployeeServices(employee.id) })

  return (
    <Modal title={`Serviços de ${employee.name}`} onClose={onClose} size="lg">
      <p className="text-sm leading-6 text-text-600">Defina quais serviços esse profissional pode realizar e receber na agenda.</p>
      {(catalog.isLoading || assigned.isLoading) && <p className="py-10 text-center text-sm text-text-500">Carregando serviços...</p>}
      {(catalog.isError || assigned.isError) && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{getApiErrorMessage(catalog.error ?? assigned.error, 'Não foi possível carregar os serviços.')}</p>}
      {catalog.data && assigned.data && <ServicesEditor employee={employee} catalog={catalog.data} assigned={assigned.data} onClose={onClose} onSaved={() => queryClient.invalidateQueries({ queryKey: ['employee-services', employee.id] })} />}
    </Modal>
  )
}

/** Mantém a seleção de serviços do profissional e envia o conjunto final à API. */
function ServicesEditor({ employee, catalog, assigned, onClose, onSaved }: { employee: Employee; catalog: SalonService[]; assigned: ProfessionalServices; onClose: () => void; onSaved: () => void }) {
  const [allServices, setAllServices] = useState(assigned.allServices)
  const [selectedIds, setSelectedIds] = useState(() => assigned.services.map((service) => service.id))
  const save = useMutation({ mutationFn: () => updateEmployeeServices(employee.id, allServices, allServices ? [] : selectedIds), onSuccess: () => { onSaved(); onClose() } })
  const toggle = (id: number) => setSelectedIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id])

  return (
    <>
      <label className={`mt-6 flex cursor-pointer items-start gap-3 rounded-xl border p-4 ${allServices ? 'border-primary-300 bg-primary-50' : 'border-background-300 bg-background-100/50'}`}>
        <input type="checkbox" checked={allServices} onChange={(event) => setAllServices(event.target.checked)} className="mt-1 size-4 accent-primary-600" />
        <span className="flex-1">
          <strong className="flex items-center gap-2 text-sm text-text-900"><Layers3 size={16} className="text-primary-600" /> Todos os serviços</strong>
          <span className="mt-1 block text-xs leading-5 text-text-600">Inclui automaticamente novos serviços cadastrados no futuro.</span>
        </span>
      </label>

      <div className="mt-4 grid max-h-80 gap-2 overflow-y-auto pr-1 sm:grid-cols-2">
        {catalog.map((service) => {
          const checked = allServices || selectedIds.includes(service.id)
          return (
            <label key={service.id} className={`flex cursor-pointer items-center gap-3 rounded-xl border p-3.5 ${checked ? 'border-primary-200 bg-primary-50/70' : 'border-background-300 bg-background-50'} ${allServices ? 'opacity-70' : ''}`}>
              <input type="checkbox" disabled={allServices} checked={checked} onChange={() => toggle(service.id)} className="size-4 accent-primary-600" />
              <span className="min-w-0 flex-1 truncate text-sm font-medium text-text-800">{service.name}</span>
              {checked && <Check size={15} className="shrink-0 text-primary-600" />}
            </label>
          )
        })}
        {catalog.length === 0 && <p className="sm:col-span-2 py-8 text-center text-sm text-text-500">Cadastre serviços antes de fazer uma seleção individual.</p>}
      </div>

      {save.isError && <p role="alert" className="mt-4 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{getApiErrorMessage(save.error, 'Não foi possível salvar os serviços.')}</p>}
      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" onClick={onClose} className="secondary-button">Cancelar</button>
        <button type="button" disabled={save.isPending} onClick={() => save.mutate()} className="primary-button">{save.isPending ? 'Salvando...' : 'Salvar serviços'}</button>
      </div>
    </>
  )
}
