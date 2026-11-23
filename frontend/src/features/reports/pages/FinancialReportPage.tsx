/** Apresenta receitas e totais financeiros para o período selecionado. */
import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { BadgeDollarSign, CalendarCheck, CalendarRange, Receipt, RotateCcw, TrendingUp, Wallet } from 'lucide-react'
import { ExportReportButtons } from '@/features/reports/components/ExportReportButtons'
import { ActiveFilters, FilterTag } from '@/features/reports/components/ReportFilters'
import { fetchEmployees } from '@/features/employees/api/employeesApi'
import { fetchAllAdministrativeServices } from '@/features/catalog/api/servicesApi'
import { fetchFinancialReport, type FinancialReportFilters, type FinancialReportLine } from '@/features/reports/api/financialReportApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatMoney } from '@/lib/format'

/**
 * Relatório financeiro/produção: quanto os atendimentos concluídos geraram no período, quanto foi de
 * comissão e o que sobra depois dela, com detalhamento por serviço e por profissional.
 */
export function FinancialReportPage() {
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [professionalId, setProfessionalId] = useState('')
  const [serviceId, setServiceId] = useState('')
  const [filters, setFilters] = useState<FinancialReportFilters>({})
  const [filterError, setFilterError] = useState<string | null>(null)

  const employees = useQuery({ queryKey: ['employees'], queryFn: fetchEmployees })
  const services = useQuery({ queryKey: ['administrative-services'], queryFn: fetchAllAdministrativeServices })
  const report = useQuery({
    queryKey: ['financial-report', filters.startDate, filters.endDate, filters.professionalId, filters.serviceId],
    queryFn: () => fetchFinancialReport(filters),
  })
  const professionalName = employees.data?.find((employee) => employee.id === filters.professionalId)?.name
  const serviceName = services.data?.find((service) => service.id === filters.serviceId)?.name
  const hasActiveFilters = Boolean(filters.startDate || filters.professionalId || filters.serviceId)

  /** Valida o intervalo antes de atualizar a consulta financeira. */
  function applyFilters(event: FormEvent) {
    event.preventDefault()
    if (Boolean(startDate) !== Boolean(endDate)) {
      setFilterError('Informe a data inicial e a data final para filtrar por período.')
      return
    }
    if (startDate && endDate && startDate > endDate) {
      setFilterError('A data inicial não pode ser posterior à data final.')
      return
    }
    setFilterError(null)
    setFilters({
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      professionalId: professionalId ? Number(professionalId) : undefined,
      serviceId: serviceId ? Number(serviceId) : undefined,
    })
  }

  function clearFilters() {
    setStartDate('')
    setEndDate('')
    setProfessionalId('')
    setServiceId('')
    setFilterError(null)
    setFilters({})
  }

  function removePeriod() {
    setStartDate('')
    setEndDate('')
    setFilters({ ...filters, startDate: undefined, endDate: undefined })
  }

  function removeProfessional() {
    setProfessionalId('')
    setFilters({ ...filters, professionalId: undefined })
  }

  function removeService() {
    setServiceId('')
    setFilters({ ...filters, serviceId: undefined })
  }

  const data = report.data
  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Financeiro</p>
        <h1 className="page-title mt-3">Relatório financeiro</h1>
        <p className="page-subtitle">Faturamento dos atendimentos concluídos, comissões e valor após comissões, por serviço e por profissional.</p>
      </header>

      <form onSubmit={applyFilters} className="surface-card mt-6 grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4">
        <Filter label="Data inicial"><input type="date" value={startDate} max={endDate || undefined} onChange={(event) => setStartDate(event.target.value)} className="filter-input" /></Filter>
        <Filter label="Data final"><input type="date" value={endDate} min={startDate || undefined} onChange={(event) => setEndDate(event.target.value)} className="filter-input" /></Filter>
        <Filter label="Profissional">
          <select value={professionalId} onChange={(event) => setProfessionalId(event.target.value)} className="filter-input">
            <option value="">Todos</option>
            {employees.data?.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}{employee.active ? '' : ' (desativado)'}</option>)}
          </select>
        </Filter>
        <Filter label="Serviço">
          <select value={serviceId} onChange={(event) => setServiceId(event.target.value)} className="filter-input">
            <option value="">Todos</option>
            {services.data?.map((service) => <option key={service.id} value={service.id}>{service.name}{service.active ? '' : ' (desativado)'}</option>)}
          </select>
        </Filter>
        {filterError && <p role="alert" className="text-sm text-danger-600 sm:col-span-2 lg:col-span-4">{filterError}</p>}
        <div className="flex flex-wrap gap-3 sm:col-span-2 lg:col-span-4">
          <button type="submit" className="primary-button">Aplicar filtros</button>
          <button type="button" onClick={clearFilters} className="secondary-button"><RotateCcw size={15} /> Limpar filtros</button>
          <ExportReportButtons path="/estabelecimentos/me/relatorio-financeiro" params={filters} filename="relatorio-financeiro" />
        </div>
      </form>

      {hasActiveFilters && (
        <ActiveFilters>
          {filters.startDate && filters.endDate && (
            <FilterTag onRemove={removePeriod}>{formatDate(filters.startDate)} a {formatDate(filters.endDate)}</FilterTag>
          )}
          {filters.professionalId && <FilterTag onRemove={removeProfessional}>{professionalName ?? 'Profissional'}</FilterTag>}
          {filters.serviceId && <FilterTag onRemove={removeService}>{serviceName ?? 'Serviço'}</FilterTag>}
        </ActiveFilters>
      )}

      {report.isLoading && <StateCard>Carregando relatório...</StateCard>}
      {report.isError && <StateCard error>{getApiErrorMessage(report.error, 'Não foi possível carregar o relatório financeiro.')}</StateCard>}

      {data && !report.isError && (
        <>
          <p className="mt-6 flex items-center gap-2 text-sm text-text-600"><CalendarRange size={16} /> Período: {formatDate(data.periodStart)} a {formatDate(data.periodEnd)} (pela data de conclusão)</p>
          <section className="surface-card mt-3 grid overflow-hidden sm:grid-cols-2 lg:grid-cols-5">
            <SummaryCard icon={TrendingUp} label="Faturamento bruto" value={formatMoney(data.grossRevenue)} />
            <SummaryCard icon={BadgeDollarSign} label="Comissões" value={formatMoney(data.totalCommission)} bordered />
            <SummaryCard icon={Wallet} label="Valor após comissões" value={formatMoney(data.revenueAfterCommission)} bordered highlight />
            <SummaryCard icon={CalendarCheck} label="Atendimentos concluídos" value={String(data.completedCount)} bordered />
            <SummaryCard icon={Receipt} label="Ticket médio" value={formatMoney(data.averageTicket)} bordered />
          </section>

          {data.completedCount === 0
            ? <StateCard>Nenhum atendimento concluído encontrado para os filtros selecionados.</StateCard>
            : (
              <div className="mt-6 grid gap-6 xl:grid-cols-2">
                <BreakdownTable title="Por serviço" nameLabel="Serviço" lines={data.byService} total={data.grossRevenue} />
                <BreakdownTable title="Por profissional" nameLabel="Profissional" lines={data.byProfessional} total={data.grossRevenue} showAverageTicket />
              </div>
            )}
        </>
      )}
    </div>
  )
}

/**
 * Detalha os valores que compõem o total financeiro do período. Cada item vira um bloco empilhado
 * (nome e faturamento em destaque, barra de participação e métricas secundárias abaixo), então
 * cabe em qualquer largura sem rolagem horizontal. O ticket médio só aparece por profissional: por
 * serviço ele repete o preço do serviço e não acrescenta informação.
 */
function BreakdownTable({ title, nameLabel, lines, total, showAverageTicket = false }: {
  title: string; nameLabel: string; lines: FinancialReportLine[]; total: number; showAverageTicket?: boolean
}) {
  return (
    <section className="surface-card overflow-hidden">
      <div className="flex items-baseline justify-between gap-3 border-b border-background-300 px-5 py-4">
        <h2 className="font-semibold text-text-900">{title}</h2>
        <span className="text-xs text-text-500">{lines.length} {lines.length === 1 ? 'item' : 'itens'}</span>
      </div>
      <ul className="divide-y divide-background-300" aria-label={`${title}: faturamento por ${nameLabel.toLowerCase()}`}>
        {lines.map((line) => {
          const share = total > 0 ? (line.revenue / total) * 100 : 0
          return (
            <li key={line.id} className="px-5 py-4">
              <div className="flex items-baseline justify-between gap-3">
                <span className="min-w-0 truncate font-medium text-text-900" title={line.name}>{line.name}</span>
                <span className="shrink-0 font-semibold tabular-nums text-text-950">{formatMoney(line.revenue)}</span>
              </div>
              <div className="mt-2 flex items-center gap-3">
                <span className="block h-1.5 flex-1 rounded-full bg-background-200" aria-hidden="true">
                  <span className="block h-1.5 rounded-full bg-primary-500" style={{ width: `${share > 0 ? Math.max(2, share) : 0}%` }} />
                </span>
                <span className="w-24 shrink-0 text-right text-xs tabular-nums text-text-500">{formatShare(share)} do total</span>
              </div>
              <dl className="mt-3 grid grid-cols-3 gap-3 text-xs">
                <Metric label="Atendimentos" value={String(line.completedCount)} />
                {showAverageTicket && <Metric label="Ticket médio" value={formatMoney(line.averageTicket)} />}
                {/* Comissões fica sempre na última coluna, alinhada à direita, logo abaixo do faturamento. */}
                <Metric label="Comissões" value={formatMoney(line.commission)} className="col-start-3 text-right" />
              </dl>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function Metric({ label, value, className = '' }: { label: string; value: string; className?: string }) {
  return <div className={`min-w-0 ${className}`}><dt className="text-text-500">{label}</dt><dd className="mt-0.5 truncate font-medium tabular-nums text-text-800">{value}</dd></div>
}
function formatShare(value: number) { return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 }).format(value)}%` }

function Filter({ label, children }: { label: string; children: React.ReactNode }) { return <label className="text-sm font-medium text-text-800">{label}{children}</label> }
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`mt-6 rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div> }
function SummaryCard({ icon: Icon, label, value, bordered = false, highlight = false }: { icon: typeof Wallet; label: string; value: string; bordered?: boolean; highlight?: boolean }) {
  return <div className={`p-5 ${bordered ? 'border-t border-background-300 sm:border-l sm:border-t-0' : ''} ${highlight ? 'bg-primary-50' : ''}`}><Icon size={18} className="text-primary-700" /><p className="mt-4 text-[0.65rem] font-extrabold uppercase tracking-[0.12em] text-text-500">{label}</p><p className="mt-1 text-xl font-extrabold tracking-[-0.03em] text-text-950">{value}</p></div>
}
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`)) }
