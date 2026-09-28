import { useState, type FormEvent, type ReactNode } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { BadgeDollarSign, CalendarRange, CircleDollarSign, ClipboardCheck, RotateCcw, X } from 'lucide-react'
import {
  fetchMyCompletedServices,
  type CompletedServiceEntry,
  type CompletedServicesFilters,
} from '@/features/appointments/completedServicesApi'
import { getApiErrorMessage } from '@/lib/apiError'

/**
 * US 2.15 — Visualizar atendimentos realizados (visão do profissional).
 * Sem filtros, mostra o mês corrente. "Limpar filtros" volta para o mês corrente (RNF.03.03).
 * Lista em ordem do mais recente para o mais antigo (RNF.01.01), em cartões no celular
 * e tabela em telas maiores.
 */
export function MyCompletedServicesPage() {
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [serviceId, setServiceId] = useState('')
  const [filters, setFilters] = useState<CompletedServicesFilters>({})
  const [filterError, setFilterError] = useState<string | null>(null)

  const query = useQuery({
    queryKey: ['my-completed-services', filters.startDate, filters.endDate, filters.serviceId],
    queryFn: () => fetchMyCompletedServices(filters),
    placeholderData: keepPreviousData,
  })

  const today = todayIso()
  const data = query.data
  const hasActiveFilters = Boolean(filters.startDate || filters.serviceId)
  const selectedServiceName = data?.availableServices.find((service) => service.id === filters.serviceId)?.name

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    if (Boolean(startDate) !== Boolean(endDate)) {
      setFilterError('Informe a data inicial e a data final para filtrar por período.')
      return
    }
    if (startDate && endDate && startDate > endDate) {
      setFilterError('Período informado é inválido: a data inicial é posterior à data final.')
      return
    }
    setFilterError(null)
    setFilters({
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      serviceId: serviceId ? Number(serviceId) : undefined,
    })
  }

  function clearFilters() {
    setStartDate('')
    setEndDate('')
    setServiceId('')
    setFilterError(null)
    setFilters({})
  }

  function removePeriod() {
    setStartDate('')
    setEndDate('')
    setFilters((current) => ({ ...current, startDate: undefined, endDate: undefined }))
  }

  function removeService() {
    setServiceId('')
    setFilters((current) => ({ ...current, serviceId: undefined }))
  }

  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Meu desempenho</p>
        <h1 className="page-title mt-3">Atendimentos realizados</h1>
        <p className="page-subtitle">Acompanhe os serviços que você concluiu e as comissões geradas. Sem filtros, mostramos o mês atual.</p>
      </header>

      <form onSubmit={applyFilters} className="surface-card mt-6 grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-3">
        <Filter label="Data inicial (DD/MM/AAAA)">
          <input type="date" value={startDate} max={endDate || today} onChange={(event) => setStartDate(event.target.value)} className="filter-input" />
        </Filter>
        <Filter label="Data final (DD/MM/AAAA)">
          <input type="date" value={endDate} min={startDate || undefined} max={today} onChange={(event) => setEndDate(event.target.value)} className="filter-input" />
        </Filter>
        <Filter label="Tipo de serviço">
          <select value={serviceId} onChange={(event) => setServiceId(event.target.value)} className="filter-input">
            <option value="">Todos</option>
            {data?.availableServices.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}
          </select>
        </Filter>
        {filterError && <p role="alert" className="text-sm text-danger-600 sm:col-span-2 lg:col-span-3">{filterError}</p>}
        <div className="flex flex-wrap gap-3 sm:col-span-2 lg:col-span-3">
          <button type="submit" className="primary-button">Aplicar filtros</button>
          <button type="button" onClick={clearFilters} className="secondary-button"><RotateCcw size={15} /> Limpar filtros</button>
        </div>
      </form>

      {hasActiveFilters && data && (
        <div className="mt-4 flex flex-wrap items-center gap-2" aria-label="Filtros ativos">
          <span className="text-xs font-bold uppercase tracking-[0.12em] text-text-500">Filtros ativos:</span>
          {filters.startDate && (
            <FilterTag onRemove={removePeriod}>{formatDate(data.startDate)} a {formatDate(data.endDate)}</FilterTag>
          )}
          {filters.serviceId && <FilterTag onRemove={removeService}>{selectedServiceName ?? 'Serviço'}</FilterTag>}
        </div>
      )}

      {query.isLoading && <StateCard>Carregando atendimentos...</StateCard>}
      {query.isError && <StateCard error>{getApiErrorMessage(query.error, 'Não foi possível carregar seus atendimentos.')}</StateCard>}

      {data && !query.isError && (
        <>
          <section className="surface-card mt-6 grid overflow-hidden sm:grid-cols-2 lg:grid-cols-4" aria-label="Resumo">
            <SummaryCard icon={CalendarRange} label="Período" value={`${formatDate(data.startDate)} a ${formatDate(data.endDate)}`} />
            <SummaryCard icon={ClipboardCheck} label="Serviços realizados" value={String(data.summary.totalServices)} bordered />
            <SummaryCard icon={CircleDollarSign} label="Valor total dos serviços" value={formatMoney(data.summary.totalServiceValue)} bordered />
            <SummaryCard icon={BadgeDollarSign} label="Total de comissões" value={formatMoney(data.summary.totalCommission)} bordered highlight />
          </section>

          {data.entries.length === 0 ? (
            <StateCard>Nenhum serviço encontrado.</StateCard>
          ) : (
            <>
              <ul className="mt-6 space-y-3 md:hidden" aria-label="Atendimentos realizados">
                {data.entries.map((entry) => <EntryCard key={entry.appointmentId} entry={entry} />)}
              </ul>

              <div className="surface-card mt-6 hidden overflow-x-auto md:block">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-background-300 bg-background-100/70 text-xs uppercase tracking-wide text-text-600">
                    <tr>
                      <th className="px-5 py-3 font-semibold">Data</th>
                      <th className="px-5 py-3 font-semibold">Serviço</th>
                      <th className="px-5 py-3 font-semibold">Cliente</th>
                      <th className="px-5 py-3 text-right font-semibold">Valor</th>
                      <th className="px-5 py-3 text-right font-semibold">Comissão (%)</th>
                      <th className="px-5 py-3 text-right font-semibold">Comissão (R$)</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-background-300">
                    {data.entries.map((entry) => (
                      <tr key={entry.appointmentId} className="text-text-800">
                        <td className="whitespace-nowrap px-5 py-3">{formatDateTime(entry.completedAt)}</td>
                        <td className="px-5 py-3 font-medium text-text-900">{entry.serviceName}</td>
                        <td className="px-5 py-3">{entry.clientName}</td>
                        <td className="whitespace-nowrap px-5 py-3 text-right">{formatMoney(entry.servicePrice)}</td>
                        <td className="whitespace-nowrap px-5 py-3 text-right">{formatPercentage(entry.commissionPercentage)}</td>
                        <td className="whitespace-nowrap px-5 py-3 text-right font-semibold text-text-950">{formatMoney(entry.commissionAmount)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot className="border-t-2 border-background-300 bg-background-100/70 font-bold text-text-950">
                    <tr>
                      <td className="px-5 py-3" colSpan={3}>Total ({data.summary.totalServices} {data.summary.totalServices === 1 ? 'serviço' : 'serviços'})</td>
                      <td className="whitespace-nowrap px-5 py-3 text-right">{formatMoney(data.summary.totalServiceValue)}</td>
                      <td className="px-5 py-3" />
                      <td className="whitespace-nowrap px-5 py-3 text-right">{formatMoney(data.summary.totalCommission)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}

function EntryCard({ entry }: { entry: CompletedServiceEntry }) {
  return (
    <li className="surface-card p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-semibold text-text-900">{entry.serviceName}</p>
          <p className="mt-0.5 truncate text-sm text-text-600">{entry.clientName}</p>
        </div>
        <p className="shrink-0 text-xs font-medium text-text-500">{formatDateTime(entry.completedAt)}</p>
      </div>
      <dl className="mt-3 grid grid-cols-3 gap-2 border-t border-background-300 pt-3 text-sm">
        <div><dt className="text-xs text-text-500">Valor</dt><dd className="font-medium text-text-800">{formatMoney(entry.servicePrice)}</dd></div>
        <div><dt className="text-xs text-text-500">Comissão</dt><dd className="font-medium text-text-800">{formatPercentage(entry.commissionPercentage)}</dd></div>
        <div className="text-right"><dt className="text-xs text-text-500">Você recebe</dt><dd className="font-bold text-text-950">{formatMoney(entry.commissionAmount)}</dd></div>
      </dl>
    </li>
  )
}

function Filter({ label, children }: { label: string; children: ReactNode }) {
  return <label className="text-sm font-medium text-text-800">{label}{children}</label>
}

function FilterTag({ children, onRemove }: { children: ReactNode; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-primary-200 bg-primary-50 py-1 pl-3 pr-1.5 text-xs font-semibold text-primary-800">
      {children}
      <button type="button" onClick={onRemove} className="grid size-5 place-items-center rounded-full hover:bg-primary-100" aria-label="Remover filtro"><X size={12} /></button>
    </span>
  )
}

function StateCard({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return <div className={`mt-6 rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div>
}

function SummaryCard({ icon: Icon, label, value, bordered = false, highlight = false }: {
  icon: typeof CalendarRange
  label: string
  value: string
  bordered?: boolean
  highlight?: boolean
}) {
  return (
    <div className={`p-5 ${bordered ? 'border-t border-background-300 sm:border-l sm:border-t-0' : ''} ${highlight ? 'bg-primary-50/60' : ''}`}>
      <Icon size={18} className="text-primary-700" />
      <p className="mt-4 text-[0.65rem] font-extrabold uppercase tracking-[0.12em] text-text-500">{label}</p>
      <p className="mt-2 text-xl font-extrabold tracking-[-0.035em] text-text-950">{value}</p>
    </div>
  )
}

function todayIso() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}

function formatPercentage(value: number) {
  return new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value) + '%'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`))
}

/** completedAt vem sem fuso (horário local do estabelecimento); exibimos como está. */
function formatDateTime(value: string) {
  const [date, time = ''] = value.split('T')
  return `${formatDate(date)} ${time.slice(0, 5)}`.trim()
}
