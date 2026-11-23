/** Resume os atendimentos concluídos e dá acesso aos respectivos detalhes. */
import { useState, type FormEvent, type ReactNode } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { BadgeDollarSign, ChevronLeft, ChevronRight, CircleDollarSign, ClipboardCheck, RotateCcw } from 'lucide-react'
import { fetchEmployees } from '@/features/employees/api/employeesApi'
import { ExportReportButtons } from '@/features/reports/components/ExportReportButtons'
import { ActiveFilters, FilterTag } from '@/features/reports/components/ReportFilters'
import {
  fetchCompletedAppointments,
  type CompletedAppointmentEntry,
  type CompletedAppointmentsFilters,
} from '@/features/reports/api/completedAppointmentsApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatDate, formatDateTime, formatMoney, formatPercentage, todayIso } from '@/lib/format'

/**
 * US 2.13 — Visualizar e filtrar atendimentos dos funcionários.
 * Só atendimentos finalizados; filtro por funcionário e por período (data de conclusão),
 * combináveis; sem filtros, mostra todo o histórico. Paginação no servidor (RNF.03.02)
 * e totais do rodapé calculados sobre todos os resultados filtrados.
 */
export function CompletedAppointmentsPanel() {
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [professionalId, setProfessionalId] = useState('')
  const [filters, setFilters] = useState<CompletedAppointmentsFilters>({})
  const [page, setPage] = useState(0)
  const [filterError, setFilterError] = useState<string | null>(null)

  const employees = useQuery({ queryKey: ['employees'], queryFn: fetchEmployees })
  const query = useQuery({
    queryKey: ['completed-appointments', filters.startDate, filters.endDate, filters.professionalId, page],
    queryFn: () => fetchCompletedAppointments(filters, page),
    placeholderData: keepPreviousData,
  })

  const today = todayIso()
  const data = query.data
  const professionalName = employees.data?.find((employee) => employee.id === filters.professionalId)?.name
  const hasActiveFilters = Boolean(filters.startDate || filters.professionalId)

  function updateFilters(next: CompletedAppointmentsFilters) {
    setFilters(next)
    setPage(0)
  }

  /** Só aplica o período ao relatório quando as duas datas são válidas. */
  function applyFilters(event: FormEvent) {
    event.preventDefault()
    if (Boolean(startDate) !== Boolean(endDate)) {
      setFilterError('Informe a data inicial e a data final para filtrar por período.')
      return
    }
    if (startDate && endDate && startDate > endDate) {
      setFilterError('Período informado é inválido')
      return
    }
    setFilterError(null)
    updateFilters({
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      professionalId: professionalId ? Number(professionalId) : undefined,
    })
  }

  function clearFilters() {
    setStartDate('')
    setEndDate('')
    setProfessionalId('')
    setFilterError(null)
    updateFilters({})
  }

  function removePeriod() {
    setStartDate('')
    setEndDate('')
    updateFilters({ ...filters, startDate: undefined, endDate: undefined })
  }

  function removeProfessional() {
    setProfessionalId('')
    updateFilters({ ...filters, professionalId: undefined })
  }

  return (
    <>
      <form onSubmit={applyFilters} className="surface-card mt-6 grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-3">
        <Filter label="Concluídos a partir de">
          <input type="date" value={startDate} max={endDate || today} onChange={(event) => setStartDate(event.target.value)} className="filter-input" />
        </Filter>
        <Filter label="Concluídos até">
          <input type="date" value={endDate} min={startDate || undefined} max={today} onChange={(event) => setEndDate(event.target.value)} className="filter-input" />
        </Filter>
        <Filter label="Funcionário">
          <select value={professionalId} onChange={(event) => setProfessionalId(event.target.value)} className="filter-input">
            <option value="">Todos</option>
            {employees.data?.map((employee) => (
              <option key={employee.id} value={employee.id}>{employee.name}{employee.active ? '' : ' (desativado)'}</option>
            ))}
          </select>
        </Filter>
        {employees.isError && <p role="alert" className="text-sm text-danger-600 sm:col-span-2 lg:col-span-3">{getApiErrorMessage(employees.error, 'Não foi possível carregar os funcionários.')}</p>}
        {filterError && <p role="alert" className="text-sm text-danger-600 sm:col-span-2 lg:col-span-3">{filterError}</p>}
        <div className="flex flex-wrap gap-3 sm:col-span-2 lg:col-span-3">
          <button type="submit" className="primary-button">Aplicar filtros</button>
          <button type="button" onClick={clearFilters} className="secondary-button"><RotateCcw size={15} /> Limpar filtros</button>
          <ExportReportButtons path="/estabelecimentos/me/atendimentos-realizados" params={filters} filename="atendimentos-realizados" formats={['pdf']} />
        </div>
      </form>

      {hasActiveFilters && (
        <ActiveFilters>
          {filters.startDate && filters.endDate && (
            <FilterTag onRemove={removePeriod}>{formatDate(filters.startDate)} a {formatDate(filters.endDate)}</FilterTag>
          )}
          {filters.professionalId && <FilterTag onRemove={removeProfessional}>{professionalName ?? 'Funcionário'}</FilterTag>}
        </ActiveFilters>
      )}

      {query.isLoading && <StateCard>Carregando atendimentos...</StateCard>}
      {query.isError && <StateCard error>{getApiErrorMessage(query.error, 'Não foi possível carregar os atendimentos.')}</StateCard>}

      {data && !query.isError && (
        <>
          <section className="surface-card mt-6 grid overflow-hidden sm:grid-cols-3" aria-label="Totais">
            <SummaryCard icon={ClipboardCheck} label={hasActiveFilters ? 'Atendimentos no filtro' : 'Atendimentos finalizados'} value={String(data.totalElements)} />
            <SummaryCard icon={CircleDollarSign} label="Valor total" value={formatMoney(data.totalServiceValue)} bordered />
            <SummaryCard icon={BadgeDollarSign} label="Total de comissões" value={formatMoney(data.totalCommission)} bordered />
          </section>

          {data.entries.length === 0 ? (
            <StateCard>
              {hasActiveFilters
                ? 'Nenhum resultado encontrado'
                : 'Nenhum atendimento foi realizado ainda. Assim que a equipe concluir atendimentos, eles aparecerão aqui.'}
            </StateCard>
          ) : (
            <>
              <ul className="mt-6 space-y-3 md:hidden" aria-label="Atendimentos finalizados">
                {data.entries.map((entry) => <EntryCard key={entry.appointmentId} entry={entry} />)}
              </ul>

              <div className="surface-card mt-6 hidden overflow-x-auto md:block">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-background-300 bg-background-100/70 text-xs uppercase tracking-wide text-text-600">
                    <tr>
                      <th className="px-5 py-3 font-semibold">Funcionário</th>
                      <th className="px-5 py-3 font-semibold">Data</th>
                      <th className="px-5 py-3 font-semibold">Serviço</th>
                      <th className="px-5 py-3 font-semibold">Cliente</th>
                      <th className="px-5 py-3 text-right font-semibold">Valor</th>
                      <th className="px-5 py-3 text-right font-semibold">Comissão</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-background-300">
                    {data.entries.map((entry) => (
                      <tr key={entry.appointmentId} className="text-text-800">
                        <td className="px-5 py-3 font-medium text-text-900">{entry.professionalName}</td>
                        <td className="whitespace-nowrap px-5 py-3">{formatDateTime(entry.completedAt)}</td>
                        <td className="px-5 py-3">{entry.serviceName}</td>
                        <td className="px-5 py-3">{entry.clientName}</td>
                        <td className="whitespace-nowrap px-5 py-3 text-right">{formatMoney(entry.servicePrice)}</td>
                        <td className="whitespace-nowrap px-5 py-3 text-right">
                          <span className="font-semibold text-text-950">{formatMoney(entry.commissionAmount)}</span>
                          <span className="ml-1.5 text-xs text-text-500">({formatPercentage(entry.commissionPercentage)})</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot className="border-t-2 border-background-300 bg-background-100/70 font-bold text-text-950">
                    <tr>
                      <td className="px-5 py-3" colSpan={4}>Total ({data.totalElements} {data.totalElements === 1 ? 'atendimento' : 'atendimentos'})</td>
                      <td className="whitespace-nowrap px-5 py-3 text-right">{formatMoney(data.totalServiceValue)}</td>
                      <td className="whitespace-nowrap px-5 py-3 text-right">{formatMoney(data.totalCommission)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>

              {data.totalPages > 1 && (
                <nav className="mt-4 flex items-center justify-between gap-3" aria-label="Paginação">
                  <button type="button" className="secondary-button" disabled={page === 0 || query.isFetching} onClick={() => setPage((current) => Math.max(0, current - 1))}>
                    <ChevronLeft size={16} /> Anterior
                  </button>
                  <span className="text-sm text-text-600">Página {data.page + 1} de {data.totalPages}</span>
                  <button type="button" className="secondary-button" disabled={page + 1 >= data.totalPages || query.isFetching} onClick={() => setPage((current) => current + 1)}>
                    Próxima <ChevronRight size={16} />
                  </button>
                </nav>
              )}
            </>
          )}
        </>
      )}
    </>
  )
}

/** Mostra valores e detalhes do atendimento concluído em uma linha do relatório. */
function EntryCard({ entry }: { entry: CompletedAppointmentEntry }) {
  return (
    <li className="surface-card p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-semibold text-text-900">{entry.professionalName}</p>
          <p className="mt-0.5 truncate text-sm text-text-600">{entry.serviceName} · {entry.clientName}</p>
        </div>
        <p className="shrink-0 text-xs font-medium text-text-500">{formatDateTime(entry.completedAt)}</p>
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-2 border-t border-background-300 pt-3 text-sm">
        <div><dt className="text-xs text-text-500">Valor</dt><dd className="font-medium text-text-800">{formatMoney(entry.servicePrice)}</dd></div>
        <div className="text-right"><dt className="text-xs text-text-500">Comissão ({formatPercentage(entry.commissionPercentage)})</dt><dd className="font-bold text-text-950">{formatMoney(entry.commissionAmount)}</dd></div>
      </dl>
    </li>
  )
}

function Filter({ label, children }: { label: string; children: ReactNode }) {
  return <label className="text-sm font-medium text-text-800">{label}{children}</label>
}

function StateCard({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return <div className={`mt-6 rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div>
}

function SummaryCard({ icon: Icon, label, value, bordered = false }: { icon: typeof ClipboardCheck; label: string; value: string; bordered?: boolean }) {
  return (
    <div className={`p-5 ${bordered ? 'border-t border-background-300 sm:border-l sm:border-t-0' : ''}`}>
      <Icon size={18} className="text-primary-700" />
      <p className="mt-4 text-[0.65rem] font-extrabold uppercase tracking-[0.12em] text-text-500">{label}</p>
      <p className="mt-2 text-xl font-extrabold tracking-[-0.035em] text-text-950">{value}</p>
    </div>
  )
}
