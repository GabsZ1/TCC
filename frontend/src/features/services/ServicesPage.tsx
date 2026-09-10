import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock3, Pencil, Plus, Trash2 } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ServiceFormModal } from '@/features/services/ServiceFormModal'
import { createService, deactivateService, fetchServices, updateService, type SalonService, type SalonServiceInput } from '@/features/services/servicesApi'
import { getApiErrorMessage } from '@/lib/apiError'

const QUERY_KEY = ['services']

export function ServicesPage() {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<SalonService | 'new' | null>(null)
  const [deleting, setDeleting] = useState<SalonService | null>(null)
  const [error, setError] = useState<string | null>(null)
  const servicesQuery = useQuery({ queryKey: QUERY_KEY, queryFn: fetchServices })
  const services = useMemo(() => [...(servicesQuery.data ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')), [servicesQuery.data])

  const saveMutation = useMutation({
    mutationFn: (input: SalonServiceInput) => editing === 'new' ? createService(input) : updateService((editing as SalonService).id, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY })
      setEditing(null)
      setError(null)
    },
    onError: (requestError) => setError(getApiErrorMessage(requestError, 'Não foi possível salvar o serviço.')),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deactivateService(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY })
      setDeleting(null)
    },
  })

  return (
    <PageContainer>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div><h1 className="page-title">Serviços</h1><p className="page-subtitle">Defina o que pode ser agendado e quanto tempo cada atendimento ocupa.</p></div>
        <button onClick={() => setEditing('new')} className="primary-button"><Plus size={18} /> Novo serviço</button>
      </div>

      <div className="mt-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {servicesQuery.isLoading && <StateCard>Carregando serviços...</StateCard>}
        {servicesQuery.isError && <StateCard error>{getApiErrorMessage(servicesQuery.error, 'Não foi possível carregar os serviços.')}</StateCard>}
        {!servicesQuery.isLoading && !servicesQuery.isError && services.length === 0 && <StateCard>Nenhum serviço cadastrado. Cadastre o primeiro para liberar agendamentos.</StateCard>}
        {services.map((service) => (
          <article key={service.id} className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <div><h2 className="text-lg font-semibold text-brand-900">{service.name}</h2><p className="mt-1 text-sm text-brand-600">{service.description || 'Sem descrição'}</p></div>
              <span className="rounded-full bg-sage-50 px-2.5 py-1 text-xs font-semibold text-sage-600">Ativo</span>
            </div>
            <div className="mt-5 flex items-center justify-between border-t border-brand-100 pt-4">
              <div><p className="font-semibold text-brand-900">{formatMoney(service.price)}</p><p className="mt-1 flex items-center gap-1 text-sm text-brand-600"><Clock3 size={15} /> {service.durationMinutes} min · {service.commissionPercentage}% comissão</p></div>
              <div className="flex gap-1">
                <button onClick={() => setEditing(service)} className="icon-button" aria-label={`Editar ${service.name}`}><Pencil size={17} /></button>
                <button onClick={() => setDeleting(service)} className="icon-button text-danger-600" aria-label={`Remover ${service.name}`}><Trash2 size={17} /></button>
              </div>
            </div>
          </article>
        ))}
      </div>

      {editing && <ServiceFormModal service={editing === 'new' ? null : editing} isSubmitting={saveMutation.isPending} errorMessage={error} onSubmit={(values) => saveMutation.mutate(values)} onClose={() => { setEditing(null); setError(null) }} />}
      {deleting && <ConfirmDialog title="Remover serviço" message={`O serviço “${deleting.name}” deixará de aparecer para novos agendamentos.`} confirmLabel="Remover" isLoading={deleteMutation.isPending} onConfirm={() => deleteMutation.mutate(deleting.id)} onCancel={() => setDeleting(null)} />}
    </PageContainer>
  )
}

function PageContainer({ children }: { children: React.ReactNode }) { return <div className="p-5 sm:p-7 lg:p-9">{children}</div> }
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`sm:col-span-2 xl:col-span-3 rounded-2xl border bg-white p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-brand-100 text-brand-600'}`}>{children}</div> }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
