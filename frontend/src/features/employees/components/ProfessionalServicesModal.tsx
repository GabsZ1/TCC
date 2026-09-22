import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { fetchServices, type SalonService } from '@/features/services/servicesApi'
import { fetchEmployeeServices, updateEmployeeServices, type Employee, type ProfessionalServices } from '@/features/employees/services/employeesApi'
import { getApiErrorMessage } from '@/lib/apiError'

export function ProfessionalServicesModal({ employee, onClose }: { employee: Employee; onClose: () => void }) {
  const queryClient = useQueryClient()
  const catalog = useQuery({ queryKey: ['services'], queryFn: fetchServices })
  const assigned = useQuery({ queryKey: ['employee-services', employee.id], queryFn: () => fetchEmployeeServices(employee.id) })
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-overlay p-4" role="dialog" aria-modal="true" aria-labelledby="services-title">
      <div className="w-full max-w-xl rounded-2xl bg-background-50 p-6 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div><h2 id="services-title" className="font-[var(--font-display)] text-xl text-text-900">Serviços de {employee.name}</h2><p className="mt-1 text-sm text-text-600">Defina quais serviços este profissional pode realizar.</p></div>
          <button type="button" onClick={onClose} aria-label="Fechar"><X className="text-text-600" size={20} /></button>
        </div>

        {(catalog.isLoading || assigned.isLoading) && <p className="py-8 text-sm text-text-600">Carregando serviços...</p>}
        {(catalog.isError || assigned.isError) && <p role="alert" className="mt-4 text-sm text-danger-600">{getApiErrorMessage(catalog.error ?? assigned.error, 'Não foi possível carregar os serviços.')}</p>}
        {catalog.data && assigned.data && <ServicesEditor employee={employee} catalog={catalog.data} assigned={assigned.data} onClose={onClose} onSaved={() => queryClient.invalidateQueries({ queryKey: ['employee-services', employee.id] })} />}
      </div>
    </div>
  )
}

function ServicesEditor({ employee, catalog, assigned, onClose, onSaved }: { employee: Employee; catalog: SalonService[]; assigned: ProfessionalServices; onClose: () => void; onSaved: () => void }) {
  const [allServices, setAllServices] = useState(assigned.allServices)
  const [selectedIds, setSelectedIds] = useState(() => assigned.services.map((service) => service.id))
  const save = useMutation({ mutationFn: () => updateEmployeeServices(employee.id, allServices, allServices ? [] : selectedIds), onSuccess: () => { onSaved(); onClose() } })
  const toggle = (id: number) => setSelectedIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id])

  return <>
    <label className="mt-6 flex cursor-pointer items-center gap-3 rounded-xl border border-background-300 p-4"><input type="checkbox" checked={allServices} onChange={(event) => setAllServices(event.target.checked)} className="h-4 w-4 accent-primary-600" /><span><strong className="block text-sm text-text-900">Selecionar todos os serviços</strong><span className="text-xs text-text-600">Inclui automaticamente novos serviços cadastrados.</span></span></label>
    <div className="mt-4 max-h-72 space-y-2 overflow-y-auto">{catalog.map((service) => <label key={service.id} className={`flex items-center gap-3 rounded-xl border p-3 ${allServices ? 'border-background-300 bg-background-200 opacity-70' : 'border-background-300'}`}><input type="checkbox" disabled={allServices} checked={allServices || selectedIds.includes(service.id)} onChange={() => toggle(service.id)} className="h-4 w-4 accent-primary-600" /><span className="text-sm font-medium text-text-800">{service.name}</span></label>)}{catalog.length === 0 && <p className="py-4 text-sm text-text-600">Cadastre serviços antes de fazer uma seleção individual.</p>}</div>
    {save.isError && <p role="alert" className="mt-4 text-sm text-danger-600">{getApiErrorMessage(save.error, 'Não foi possível salvar os serviços.')}</p>}
    <div className="mt-6 flex justify-end gap-3"><button type="button" onClick={onClose} className="rounded-lg px-4 py-2 text-sm font-medium text-text-600 hover:bg-primary-50">Cancelar</button><button type="button" disabled={save.isPending} onClick={() => save.mutate()} className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-700 disabled:opacity-50">{save.isPending ? 'Salvando...' : 'Salvar serviços'}</button></div>
  </>
}
