import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock3, Pencil, Plus, Power, RotateCcw, Scissors, WalletCards } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ServiceFormModal } from '@/features/services/ServiceFormModal'
import { createService, deactivateService, fetchAdministrativeServices, reactivateService, updateService, type SalonService, type SalonServiceInput } from '@/features/services/servicesApi'
import { getApiErrorMessage } from '@/lib/apiError'

const QUERY_KEY = ['services', 'administrative']

export function ServicesPage() {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<SalonService | 'new' | null>(null)
  const [deleting, setDeleting] = useState<SalonService | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)
  const servicesQuery = useQuery({ queryKey: QUERY_KEY, queryFn: fetchAdministrativeServices })
  const services = useMemo(() => [...(servicesQuery.data ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')), [servicesQuery.data])
  const activeCount = services.filter((service) => service.active).length

  const saveMutation = useMutation({
    mutationFn: (input: SalonServiceInput) => editing === 'new' ? createService(input) : updateService((editing as SalonService).id, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] })
      setEditing(null)
      setError(null)
    },
    onError: (requestError) => setError(getApiErrorMessage(requestError, 'Não foi possível salvar o serviço.')),
  })

  const reactivateMutation = useMutation({
    mutationFn: reactivateService,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] })
      setStatusError(null)
    },
    onError: (requestError) => setStatusError(getApiErrorMessage(requestError, 'Não foi possível reativar o serviço.')),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deactivateService(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] })
      setDeleting(null)
      setStatusError(null)
    },
    onError: (requestError) => {
      setDeleting(null)
      setStatusError(getApiErrorMessage(requestError, 'Não foi possível desativar o serviço.'))
    },
  })

  return (
    <div className="page-shell">
      <header className="flex flex-col gap-5 border-b border-background-300 pb-7 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Catálogo</p>
          <h1 className="page-title mt-2">Serviços</h1>
          <p className="page-subtitle">Organize o catálogo, valores, duração e comissão de cada serviço oferecido.</p>
        </div>
        <button onClick={() => setEditing('new')} className="primary-button w-fit"><Plus size={17} /> Novo serviço</button>
      </header>

      <div className="mt-7 flex flex-wrap gap-2">
        <span className="rounded-full border border-background-300 bg-background-50 px-3 py-1.5 text-xs font-semibold text-text-600">{services.length} no catálogo</span>
        <span className="rounded-full border border-success-500/20 bg-success-50 px-3 py-1.5 text-xs font-semibold text-success-700">{activeCount} ativos</span>
        <span className="rounded-full border border-background-300 bg-background-200 px-3 py-1.5 text-xs font-semibold text-text-600">{services.length - activeCount} inativos</span>
      </div>

      {statusError && <p role="alert" className="mt-5 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{statusError}</p>}

      <div className="mt-6 grid gap-4 sm:grid-cols-2 2xl:grid-cols-3">
        {servicesQuery.isLoading && <StateCard>Carregando serviços...</StateCard>}
        {servicesQuery.isError && <StateCard error>{getApiErrorMessage(servicesQuery.error, 'Não foi possível carregar os serviços.')}</StateCard>}
        {!servicesQuery.isLoading && !servicesQuery.isError && services.length === 0 && <StateCard>Nenhum serviço cadastrado. Cadastre o primeiro para liberar agendamentos.</StateCard>}
        {services.map((service) => (
          <article key={service.id} className={`surface-card group relative overflow-hidden p-5 transition hover:-translate-y-0.5 hover:shadow-[var(--shadow-lifted)] ${service.active ? '' : 'opacity-[0.78]'}`}>
            <div className={`absolute inset-x-0 top-0 h-1 ${service.active ? 'bg-primary-500' : 'bg-background-400'}`} />
            <div className="flex items-start justify-between gap-4 pt-1">
              <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-primary-100 text-primary-700"><Scissors size={19} /></span>
              <span className={`status-pill ${service.active ? 'bg-success-50 text-success-700' : 'bg-background-200 text-text-600'}`}>
                <span className={`size-1.5 rounded-full ${service.active ? 'bg-success-500' : 'bg-text-400'}`} />
                {service.active ? 'Ativo' : 'Inativo'}
              </span>
            </div>

            <h2 className="mt-5 text-lg font-semibold tracking-[-0.02em] text-text-950">{service.name}</h2>
            <p className="mt-1.5 min-h-10 text-sm leading-5 text-text-600">{service.description || 'Sem descrição cadastrada.'}</p>

            <div className="mt-5 grid grid-cols-2 gap-3 rounded-xl bg-background-100 p-3.5">
              <div>
                <p className="text-[0.62rem] font-bold uppercase tracking-[0.11em] text-text-400">Valor</p>
                <p className="mt-1 font-semibold text-text-900">{formatMoney(service.price)}</p>
              </div>
              <div>
                <p className="text-[0.62rem] font-bold uppercase tracking-[0.11em] text-text-400">Comissão</p>
                <p className="mt-1 flex items-center gap-1.5 font-semibold text-text-900"><WalletCards size={14} className="text-text-400" /> {service.commissionPercentage}%</p>
              </div>
            </div>

            <div className="mt-4 flex items-center justify-between border-t border-background-300 pt-4">
              <p className="flex items-center gap-1.5 text-sm font-medium text-text-600"><Clock3 size={15} /> {service.durationMinutes} min</p>
              <div className="flex gap-1">
                <button onClick={() => setEditing(service)} className="icon-button" aria-label={`Editar ${service.name}`} title="Editar"><Pencil size={16} /></button>
                <button disabled={deleteMutation.isPending || reactivateMutation.isPending} onClick={() => service.active ? setDeleting(service) : reactivateMutation.mutate(service.id)} className={`icon-button disabled:opacity-60 ${service.active ? 'hover:bg-danger-50 hover:text-danger-600' : 'hover:bg-success-50 hover:text-success-700'}`} title={service.active ? 'Desativar serviço' : 'Reativar serviço'} aria-label={`${service.active ? 'Desativar' : 'Reativar'} ${service.name}`}>{service.active ? <Power size={16} /> : <RotateCcw size={16} />}</button>
              </div>
            </div>
          </article>
        ))}
      </div>

      {editing && <ServiceFormModal service={editing === 'new' ? null : editing} isSubmitting={saveMutation.isPending} errorMessage={error} onSubmit={(values) => saveMutation.mutate(values)} onClose={() => { setEditing(null); setError(null) }} />}
      {deleting && <ConfirmDialog title="Desativar serviço" message={`O serviço “${deleting.name}” deixará de aparecer para novos agendamentos.`} confirmLabel="Desativar" isLoading={deleteMutation.isPending} onConfirm={() => deleteMutation.mutate(deleting.id)} onCancel={() => setDeleting(null)} />}
    </div>
  )
}

function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) {
  return <div className={`sm:col-span-2 2xl:col-span-3 surface-card p-10 text-center ${error ? 'text-danger-600' : 'text-text-600'}`}>{children}</div>
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}
